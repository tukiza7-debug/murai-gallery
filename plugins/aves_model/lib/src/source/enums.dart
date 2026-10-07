enum SourceState { loading, cataloguing, locatingCountries, locatingPlaces, ready }

enum SortFactor {
  // common
  date,
  size,
  path,
  // chips only
  chipName,
  count,
  // entry only
  albumItemName,
  rating,
  duration,
  // murai: extended sort factors
  dateAdded,
  type,
  resolution,
  location,
  random,
}

enum ChipSectionFactor { none, importance, mimeType, volume }

enum EntrySectionFactor {
  none,
  album,
  month,
  day,
  // unselectable, used by some sort factors
  name,
  rating,
  // murai: extended group-by factors
  year,
  type,
  location,
}

enum TileLayout { mosaic, grid, list, calendar }
