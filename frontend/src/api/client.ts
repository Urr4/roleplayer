import axios from 'axios';
import type { AttributePools, CharacterDto, NpcDto, PlayerDto, SessionDto } from '../types';

const api = axios.create({ baseURL: '/api' });

// ── Sessions ──────────────────────────────────────────────────────────────────
export const getSessions = () => api.get<SessionDto[]>('/sessions').then(r => r.data);
export const createSession = (name: string) => api.post<SessionDto>('/sessions', { name }).then(r => r.data);
export const getSession = (id: string) => api.get<SessionDto>(`/sessions/${id}`).then(r => r.data);

// ── Players ───────────────────────────────────────────────────────────────────
export const getPlayers = () => api.get<PlayerDto[]>('/players').then(r => r.data);
export const createPlayer = (name: string) => api.post<PlayerDto>('/players', { name }).then(r => r.data);

// ── Characters (global) ──────────────────────────────────────────────────────
export const getAllCharacters = () => api.get<CharacterDto[]>('/characters').then(r => r.data);

export const createCharacter = (name: string, playerId: string, sheet?: File) => {
  const form = new FormData();
  form.append('name', name);
  form.append('playerId', playerId);
  if (sheet) form.append('sheet', sheet);
  return api.post<CharacterDto>('/characters', form).then(r => r.data);
};

export const replaceCharacterSheet = (characterId: string, sheet: File) => {
  const form = new FormData();
  form.append('sheet', sheet);
  return api.post<CharacterDto>(`/characters/${characterId}/sheet`, form).then(r => r.data);
};

export const getCharacterSheetUrl = (characterId: string) =>
  api.get<{ url: string }>(`/characters/${characterId}/sheet-url`).then(r => r.data.url);

// ── Session ↔ Character links ────────────────────────────────────────────────
export const getSessionCharacters = (sessionId: string) =>
  api.get<CharacterDto[]>(`/sessions/${sessionId}/characters`).then(r => r.data);

export const linkCharacterToSession = (sessionId: string, characterId: string) =>
  api.post(`/sessions/${sessionId}/characters`, { id: characterId });

export const unlinkCharacterFromSession = (sessionId: string, characterId: string) =>
  api.delete(`/sessions/${sessionId}/characters/${characterId}`);

// ── NPCs ──────────────────────────────────────────────────────────────────────
export const getAllNpcs = () => api.get<NpcDto[]>('/npcs').then(r => r.data);

export const getRandomNpc = (name?: string) =>
  api.get<NpcDto>('/npcs/random', { params: { name } }).then(r => r.data);

export const getAttributePools = () => api.get<AttributePools>('/npcs/attribute-pools').then(r => r.data);

export const getSessionNpcs = (sessionId: string) =>
  api.get<NpcDto[]>(`/sessions/${sessionId}/npcs`).then(r => r.data);

export const saveNpcInSession = (sessionId: string, npc: Pick<NpcDto, 'name' | 'motive' | 'status' | 'mood'>) =>
  api.post<NpcDto>(`/sessions/${sessionId}/npcs`, npc).then(r => r.data);

export const importNpcIntoSession = (sessionId: string, npcId: string) =>
  api.post(`/sessions/${sessionId}/npcs/import`, { id: npcId });

export const removeNpcFromSession = (sessionId: string, npcId: string) =>
  api.delete(`/sessions/${sessionId}/npcs/${npcId}`);
