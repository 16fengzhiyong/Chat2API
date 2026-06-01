/**
 * In-Memory Collection Index
 *
 * Provides O(1) lookups for fields that are frequently used in filter/find
 * operations (the equivalent of WHERE, JOIN, and ORDER BY clauses in a
 * relational database). This avoids full linear scans on every query.
 *
 * Usage:
 *   const index = new CollectionIndex<MyEntry>({ unique: ['id'], grouped: ['status', 'providerId'] })
 *   index.rebuild(entries)
 *   index.getOne('id', someId)          // O(1) lookup
 *   index.getMany('status', 'success')  // O(1) group lookup
 */

export interface CollectionIndexOptions<T> {
  /** Fields where each value maps to exactly one entry (e.g. primary key `id`). */
  unique?: (keyof T & string)[]
  /** Fields where each value maps to multiple entries (e.g. `status`, `providerId`). */
  grouped?: (keyof T & string)[]
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export class CollectionIndex<T extends { [K in string]?: any }> {
  private readonly uniqueIndexes = new Map<string, Map<unknown, T>>()
  private readonly groupedIndexes = new Map<string, Map<unknown, T[]>>()
  private readonly uniqueFields: (keyof T & string)[]
  private readonly groupedFields: (keyof T & string)[]

  constructor(options: CollectionIndexOptions<T>) {
    this.uniqueFields = options.unique ?? []
    this.groupedFields = options.grouped ?? []

    for (const field of this.uniqueFields) {
      this.uniqueIndexes.set(field, new Map())
    }
    for (const field of this.groupedFields) {
      this.groupedIndexes.set(field, new Map())
    }
  }

  /** Rebuild all indexes from a full collection snapshot. */
  rebuild(entries: T[]): void {
    this.uniqueFields.forEach((field) => this.uniqueIndexes.get(field)!.clear())
    this.groupedFields.forEach((field) => this.groupedIndexes.get(field)!.clear())

    for (const entry of entries) {
      this.addToIndexes(entry)
    }
  }

  /** Add a single entry to the indexes. */
  add(entry: T): void {
    this.addToIndexes(entry)
  }

  /** Remove a single entry from the indexes. */
  remove(entry: T): void {
    for (const field of this.uniqueFields) {
      const idx = this.uniqueIndexes.get(field)!
      idx.delete(entry[field])
    }
    for (const field of this.groupedFields) {
      const idx = this.groupedIndexes.get(field)!
      const key = entry[field]
      const group = idx.get(key)
      if (group) {
        const filtered = group.filter((e) => e !== entry)
        if (filtered.length === 0) {
          idx.delete(key)
        } else {
          idx.set(key, filtered)
        }
      }
    }
  }

  /** Update an entry's indexed fields. Call with old and new entry references. */
  update(oldEntry: T, newEntry: T): void {
    this.remove(oldEntry)
    this.addToIndexes(newEntry)
  }

  /** O(1) lookup by a unique-indexed field. */
  getOne(field: keyof T & string, value: unknown): T | undefined {
    const idx = this.uniqueIndexes.get(field)
    return idx?.get(value)
  }

  /** O(1) group lookup by a grouped-indexed field. */
  getMany(field: keyof T & string, value: unknown): T[] {
    const idx = this.groupedIndexes.get(field)
    return idx?.get(value) ?? []
  }

  /** Clear all indexes. */
  clear(): void {
    this.uniqueFields.forEach((field) => this.uniqueIndexes.get(field)!.clear())
    this.groupedFields.forEach((field) => this.groupedIndexes.get(field)!.clear())
  }

  private addToIndexes(entry: T): void {
    for (const field of this.uniqueFields) {
      const idx = this.uniqueIndexes.get(field)!
      idx.set(entry[field], entry)
    }
    for (const field of this.groupedFields) {
      const idx = this.groupedIndexes.get(field)!
      const key = entry[field]
      const group = idx.get(key)
      if (group) {
        group.push(entry)
      } else {
        idx.set(key, [entry])
      }
    }
  }
}
