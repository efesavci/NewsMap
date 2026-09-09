const CATEGORIES = ['ALL', 'POLITICS', 'WAR', 'TECHNOLOGY', 'BUSINESS', 'HEALTH', 'OTHER'];

export default function CategoryFilters({ selectedCategory, setSelectedCategory }) {
  return (
    <div className="filters-container">
      {CATEGORIES.map(cat => (
        <button
          key={cat}
          className={`filter-pill ${selectedCategory === cat ? 'active' : ''}`}
          onClick={() => setSelectedCategory(cat)}
        >
          {cat === 'ALL' ? 'All signals' : cat.charAt(0) + cat.slice(1).toLowerCase()}
        </button>
      ))}
    </div>
  );
}
