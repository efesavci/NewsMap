"""Persistent online news-event matching."""

from embedding_service.events.event_matcher import EventMatcher, MatchDecision
from embedding_service.events.event_store import EventStore

__all__ = ["EventMatcher", "EventStore", "MatchDecision"]
