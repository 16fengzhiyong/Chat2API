/**
 * Session Index Manager
 *
 * Provides indexed access to session records. Sessions are stored via
 * electron-store but frequently queried by id, providerId, accountId,
 * and status. This manager maintains in-memory indexes (equivalent to
 * database indexes on WHERE/JOIN columns) to avoid full-array scans.
 */

import type { SessionRecord } from './types'
import { CollectionIndex } from './CollectionIndex'

export class SessionIndexManager {
  private sessions: SessionRecord[] = []
  private readonly index = new CollectionIndex<SessionRecord>({
    unique: ['id'],
    grouped: ['providerId', 'accountId', 'status'],
  })
  private initialized = false

  /** Load sessions from the backing store and rebuild indexes. */
  load(sessions: SessionRecord[]): void {
    this.sessions = sessions
    this.index.rebuild(this.sessions)
    this.initialized = true
  }

  /** Get all sessions. */
  getAll(): SessionRecord[] {
    return this.sessions
  }

  /** O(1) lookup by session id. */
  getById(id: string): SessionRecord | undefined {
    return this.index.getOne('id', id)
  }

  /** O(1) lookup by providerId. */
  getByProviderId(providerId: string): SessionRecord[] {
    return this.index.getMany('providerId', providerId)
  }

  /** O(1) lookup by accountId. */
  getByAccountId(accountId: string): SessionRecord[] {
    return this.index.getMany('accountId', accountId)
  }

  /** Get active sessions using index + time filter. */
  getActive(timeoutMs: number): SessionRecord[] {
    const now = Date.now()
    return this.index.getMany('status', 'active')
      .filter((s) => (now - s.lastActiveAt) < timeoutMs)
  }

  /** Add a session and update indexes. */
  add(session: SessionRecord): void {
    this.sessions.push(session)
    this.index.add(session)
  }

  /** Update a session in-place and refresh indexes. */
  update(id: string, updates: Partial<SessionRecord>): SessionRecord | null {
    const oldEntry = this.index.getOne('id', id)
    if (!oldEntry) return null

    const idx = this.sessions.indexOf(oldEntry)
    const newEntry: SessionRecord = { ...oldEntry, ...updates }
    this.sessions[idx] = newEntry
    this.index.update(oldEntry, newEntry)
    return newEntry
  }

  /** Remove a session by id. */
  remove(id: string): boolean {
    const entry = this.index.getOne('id', id)
    if (!entry) return false

    const idx = this.sessions.indexOf(entry)
    this.sessions.splice(idx, 1)
    this.index.remove(entry)
    return true
  }

  /** Replace entire sessions array (e.g. after cleanup). */
  replace(sessions: SessionRecord[]): void {
    this.sessions = sessions
    this.index.rebuild(this.sessions)
  }

  /** Clear all sessions and indexes. */
  clear(): void {
    this.sessions = []
    this.index.clear()
  }

  /** Whether the index has been loaded. */
  isLoaded(): boolean {
    return this.initialized
  }
}
