from __future__ import annotations

import json
import tempfile
import unittest
from datetime import datetime, timezone
from pathlib import Path
from unittest.mock import patch, Mock

import numpy as np
import torch
from embedding_service.embedding.jina_embedder import JinaEmbedder, select_device
from embedding_service.events.candidate_index import EventCandidateIndex
from embedding_service.events.event_store import EventStore
from embedding_service.events.event_matcher import EventMatcher, title_tokens, title_entities
from embedding_service.io.articles import load_articles
from embedding_service.io.embeddings import save_embeddings_h5
from embedding_service.models import Article
from embedding_service.pipelines.event_pipeline import run_event_pipeline


class DeviceTest(unittest.TestCase):
    def test_auto_prefers_cuda_then_mps_then_cpu(self):
        for cuda, mps, expected in [(True, True, 'cuda'), (False, True, 'mps'), (False, False, 'cpu')]:
            with patch('torch.cuda.is_available', return_value=cuda), patch('torch.backends.mps.is_available', return_value=mps):
                self.assertEqual(select_device(), expected)
                self.assertEqual(select_device('cpu'), 'cpu')
        with patch('torch.backends.mps.is_available', return_value=False):
            with self.assertRaises(ValueError): select_device('mps')

    def embedder(self, device, encode):
        embedder = JinaEmbedder.__new__(JinaEmbedder)
        embedder.device, embedder.task, embedder.max_length, embedder.truncate_dim = device, 'separation', 512, 2
        embedder.model = Mock()
        embedder.model.encode.side_effect = encode
        return embedder

    def test_unsupported_mps_batch_is_retried_on_cpu(self):
        def encode(batch, **kwargs):
            if kwargs['device'] == 'mps': raise NotImplementedError('Unsupported MPS operation')
            return np.array([[3., 4.]] * len(batch))
        embedder = self.embedder('mps', encode)
        with patch('torch.mps.empty_cache'):
            result = embedder.embed(['a', 'b', 'c'], 2)
        np.testing.assert_allclose(result, [[.6, .8]] * 3)
        self.assertEqual(embedder.device, 'cpu')
        self.assertEqual(embedder.model.encode.call_count, 3)

    def test_memory_pressure_reduces_batches_without_losing_articles(self):
        def encode(batch, **kwargs):
            if len(batch) > 1: raise RuntimeError('out of memory')
            return np.array([[3., 4.]])
        result = self.embedder('cpu', encode).embed(['a', 'b', 'c', 'd'], 4)
        np.testing.assert_allclose(result, [[.6, .8]] * 4)
        with self.assertRaises(RuntimeError):
            self.embedder('cpu', lambda *a, **kw: (_ for _ in ()).throw(RuntimeError('broken model'))).embed(['a'], 1)


class IncrementalTest(unittest.TestCase):
    def test_semantic_shortlist_keeps_all_eligible_matches_and_updates(self):
        rng = np.random.default_rng(7)
        vectors = rng.normal(size=(400, 32)).astype('float32')
        vectors /= np.linalg.norm(vectors, axis=1, keepdims=True)
        now = datetime(2026, 11, 5, tzinfo=timezone.utc)
        events = [dict(event_id=str(i), centroid=v, last_published_at=now.isoformat()) for i, v in enumerate(vectors)]
        index = EventCandidateIndex(events, 32)
        for vector in vectors[:20]:
            expected = {str(i) for i, v in enumerate(vectors) if np.dot(vector, v) >= .78}
            self.assertEqual({event['event_id'] for event in index.shortlist(vector, now, 336)}, expected)
        index.update({**events[0], 'centroid': vectors[1]})
        self.assertIn('0', {e['event_id'] for e in index.shortlist(vectors[1], now, 336)})
        self.assertEqual(index.shortlist(vectors[1], datetime(2027, 1, 1, tzinfo=timezone.utc), 336), [])

    def test_rejected_score_cannot_hide_valid_paraphrase(self):
        title = 'Paris council approves transport plan'
        now = datetime(2026, 11, 5, tzinfo=timezone.utc)
        candidates = [
            dict(event_id='rejected', centroid=np.array([.77, np.sqrt(1-.77**2)]), last_published_at=now.isoformat(),
                 title_tokens=title_tokens(title), entities=title_entities(title)),
            dict(event_id='accepted', centroid=np.array([.9, np.sqrt(1-.9**2)]), last_published_at=now.isoformat(),
                 title_tokens={'paris', 'council'}, entities=set()),
        ]
        matcher = EventMatcher()
        brute = matcher.match(vector=np.array([1., 0.]), title=title, published_at=now, candidates=candidates)
        indexed = matcher.match(vector=np.array([1., 0.]), title=title, published_at=now,
                                candidates=EventCandidateIndex(candidates, 2).shortlist(np.array([1., 0.]), now, 336))
        self.assertTrue(brute.accepted)
        self.assertEqual(brute.event_id, 'accepted')
        self.assertEqual(brute.event_id, indexed.event_id)

    def test_new_events_in_same_batch_match_and_retry_is_idempotent(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            articles = [Article('body', str(i), 'Paris council approves new transport plan', 'example.com',
                                {'publishTime': f'2026-11-05T0{i}:00:00+00:00'}) for i in range(3)]
            save_embeddings_h5(str(root/'batch.h5'), articles, np.array([[1., 0.], [1., .01], [1., .02]]))
            kwargs = dict(input_h5=str(root/'batch.h5'), state_db=str(root/'events.db'), output_json=str(root/'events.json'))
            first = run_event_pipeline(**kwargs)
            self.assertEqual((first['created'], first['matched']), (1, 2))
            second = run_event_pipeline(**kwargs)
            self.assertEqual((second['created'], second['matched'], second['skipped']), (0, 0, 3))
            self.assertEqual(json.loads((root/'events.json').read_text())['events'][0]['articleCount'], 3)

    def test_retry_after_partial_match_does_not_duplicate_committed_articles(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            articles = [Article('body', str(i), 'Paris council approves new transport plan', 'example.com',
                                {'publishTime': f'2026-11-05T0{i}:00:00+00:00'}) for i in range(3)]
            checkpoint = root/'batch.h5'
            save_embeddings_h5(str(checkpoint), articles, np.array([[1., 0.], [1., .01], [1., .02]]))
            kwargs = dict(input_h5=str(checkpoint), state_db=str(root/'events.db'), output_json=str(root/'events.json'))
            with patch.object(EventStore, 'attach_article', side_effect=RuntimeError('simulated worker failure')):
                with self.assertRaises(RuntimeError): run_event_pipeline(**kwargs)
            with EventStore(str(root/'events.db')) as store:
                self.assertTrue(store.has_article('0'))
                self.assertFalse(store.has_article('1'))
            result = run_event_pipeline(**kwargs)
            self.assertEqual((result['created'], result['matched'], result['skipped']), (0, 2, 1))
            event = json.loads((root/'events.json').read_text())['events'][0]
            self.assertEqual(event['articleCount'], 3)
            self.assertEqual(len({a['articleId'] for a in event['articles']}), 3)

    def test_excerpts_are_bounded_and_survive_matching_export(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root/'article.jsonl').write_text(json.dumps({'id':'excerpt-test', 'body':'evidence ' * 500,
                'title':'Flooding in Valencia, Spain', 'publishTime':'2026-11-05T12:00:00Z'})+'\n')
            articles=load_articles(directory)
            self.assertEqual(len(articles[0].metadata['excerpt']),800)
            save_embeddings_h5(str(root/'batch.h5'),articles,np.array([[1.,0.]]))
            run_event_pipeline(input_h5=str(root/'batch.h5'),state_db=str(root/'events.db'),output_json=str(root/'events.json'))
            projection=json.loads((root/'events.json').read_text())
            self.assertEqual(projection['events'][0]['articles'][0]['excerpt'], articles[0].metadata['excerpt'])

    def test_failed_artifact_write_preserves_previous_checkpoint(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)/'batch.h5'
            articles = [Article('body', 'one')]
            save_embeddings_h5(str(path), articles, np.ones((1, 2)))
            original = path.read_bytes()
            with self.assertRaises(ValueError):
                save_embeddings_h5(str(path), articles * 2, np.ones((1, 2)))
            self.assertEqual(path.read_bytes(), original)

    def test_interrupted_jsonl_tail_does_not_drop_valid_records(self):
        with tempfile.TemporaryDirectory() as directory:
            record = json.dumps({'id':'one', 'body':'valid article'})
            (Path(directory)/'input.jsonl').write_text(record + '\n' + record + '\n' + '{"id":')
            self.assertEqual(len(load_articles(directory)), 1)


if __name__ == '__main__': unittest.main()
