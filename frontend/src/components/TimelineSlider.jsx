export default function TimelineSlider({ hoursRange, setHoursRange }) {
  const sliderValue = -hoursRange;

  return (
    <div className="timeline-container">
      <div className="timeline-header">
        <span className="timeline-title">Time window</span>
        <span className="timeline-value">{hoursRange}h</span>
      </div>
      <input
        type="range"
        min="-48"
        max="-1"
        value={sliderValue}
        onChange={(e) => setHoursRange(Math.abs(parseInt(e.target.value)))}
        className="timeline-slider"
      />
      <div className="timeline-labels">
        <span>Earlier</span>
        <span>Live edge</span>
      </div>
    </div>
  );
}
