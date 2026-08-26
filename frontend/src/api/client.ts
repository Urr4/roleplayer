import axios from 'axios';
import type {
  AdventureCharacterDto,
  AdventureDto,
  AttributePools,
  CharacterDto,
  ChronicleDto,
  NpcDto,
  PlayerDto,
  RecordingDto,
  RecordingSource,
  TranscriptSegmentDto,
} from '../types';

const api = axios.create({ baseURL: '/api' });

// ── Chronicles ────────────────────────────────────────────────────────────────
export const getChronicles = () => api.get<ChronicleDto[]>('/chronicles').then(r => r.data);
export const createChronicle = (name: string) => api.post<ChronicleDto>('/chronicles', { name }).then(r => r.data);
export const getChronicle = (id: string) => api.get<ChronicleDto>(`/chronicles/${id}`).then(r => r.data);

// ── Adventures ────────────────────────────────────────────────────────────────
export const getAdventures = (chronicleId: string) =>
  api.get<AdventureDto[]>(`/chronicles/${chronicleId}/adventures`).then(r => r.data);

export const createAdventure = (chronicleId: string, name: string, characterIds: string[] = []) =>
  api.post<AdventureDto>(`/chronicles/${chronicleId}/adventures`, { name, characterIds }).then(r => r.data);

export const getAdventure = (id: string) => api.get<AdventureDto>(`/adventures/${id}`).then(r => r.data);

export const getActiveAdventure = (chronicleId: string) =>
  api
    .get<AdventureDto>(`/chronicles/${chronicleId}/adventures/active`)
    .then(r => r.data)
    .catch(err => {
      if (axios.isAxiosError(err) && err.response?.status === 404) return null;
      throw err;
    });

export const startAdventure = (id: string) => api.post<AdventureDto>(`/adventures/${id}/start`).then(r => r.data);

export const stopAdventure = (id: string) => api.post<AdventureDto>(`/adventures/${id}/stop`).then(r => r.data);

// ── Players (global) ─────────────────────────────────────────────────────────
export const getPlayers = () => api.get<PlayerDto[]>('/players').then(r => r.data);
export const createPlayer = (name: string) => api.post<PlayerDto>('/players', { name }).then(r => r.data);

// ── Characters (chronicle-scoped) ────────────────────────────────────────────
export const getAllCharacters = () => api.get<CharacterDto[]>('/characters').then(r => r.data);

export const getChronicleCharacters = (chronicleId: string) =>
  api.get<CharacterDto[]>(`/chronicles/${chronicleId}/characters`).then(r => r.data);

export const createCharacter = (chronicleId: string, name: string, playerId: string, sheet?: File) => {
  const form = new FormData();
  form.append('name', name);
  form.append('playerId', playerId);
  if (sheet) form.append('sheet', sheet);
  return api.post<CharacterDto>(`/chronicles/${chronicleId}/characters`, form).then(r => r.data);
};

export const importCharacterIntoChronicle = (chronicleId: string, characterId: string) =>
  api.post<CharacterDto>(`/chronicles/${chronicleId}/characters/import`, { characterId }).then(r => r.data);

export const replaceCharacterSheet = (characterId: string, sheet: File) => {
  const form = new FormData();
  form.append('sheet', sheet);
  return api.post<CharacterDto>(`/characters/${characterId}/sheet`, form).then(r => r.data);
};

export const getCharacterSheetUrl = (characterId: string) =>
  api.get<{ url: string }>(`/characters/${characterId}/sheet-url`).then(r => r.data.url);

// ── Adventure characters (participating characters per adventure) ──────────
export const getAdventureCharacters = (adventureId: string) =>
  api.get<AdventureCharacterDto[]>(`/adventures/${adventureId}/characters`).then(r => r.data);

export const addAdventureCharacter = (adventureId: string, characterId: string) =>
  api.post<AdventureCharacterDto>(`/adventures/${adventureId}/characters`, { characterId }).then(r => r.data);

export const removeAdventureCharacter = (adventureId: string, characterId: string) =>
  api.delete(`/adventures/${adventureId}/characters/${characterId}`);

// ── NPCs ──────────────────────────────────────────────────────────────────────
export const getAllNpcs = () => api.get<NpcDto[]>('/npcs').then(r => r.data);

export const getRandomNpc = (name?: string) =>
  api.get<NpcDto>('/npcs/random', { params: { name } }).then(r => r.data);

export const getAttributePools = () => api.get<AttributePools>('/npcs/attribute-pools').then(r => r.data);

export const getChronicleNpcs = (chronicleId: string) =>
  api.get<NpcDto[]>(`/chronicles/${chronicleId}/npcs`).then(r => r.data);

export const saveNpcInChronicle = (chronicleId: string, npc: Pick<NpcDto, 'name' | 'motive' | 'status' | 'mood'>) =>
  api.post<NpcDto>(`/chronicles/${chronicleId}/npcs`, npc).then(r => r.data);

export const importNpcIntoChronicle = (chronicleId: string, npcId: string) =>
  api.post(`/chronicles/${chronicleId}/npcs/import`, { id: npcId });

export const removeNpcFromChronicle = (chronicleId: string, npcId: string) =>
  api.delete(`/chronicles/${chronicleId}/npcs/${npcId}`);

// ── Recordings (adventure-scoped) ───────────────────────────────────────────
export const getRecordings = (adventureId: string) =>
  api.get<RecordingDto[]>(`/adventures/${adventureId}/recordings`).then(r => r.data);

export const uploadRecording = (adventureId: string, file: File) => {
  const form = new FormData();
  form.append('file', file);
  return api.post<RecordingDto>(`/adventures/${adventureId}/recordings`, form).then(r => r.data);
};

export const startRecording = (adventureId: string, source: RecordingSource, discordChannelId?: string) =>
  api.post<RecordingDto>(`/adventures/${adventureId}/recordings/start`, { source, discordChannelId }).then(r => r.data);

export const appendRecordingChunk = (adventureId: string, recordingId: string, chunk: Blob) =>
  api.post(`/adventures/${adventureId}/recordings/${recordingId}/chunk`, chunk, {
    headers: { 'Content-Type': 'application/octet-stream' },
  });

export const pauseRecording = (adventureId: string, recordingId: string) =>
  api.post<RecordingDto>(`/adventures/${adventureId}/recordings/${recordingId}/pause`).then(r => r.data);

export const resumeRecording = (adventureId: string, recordingId: string) =>
  api.post<RecordingDto>(`/adventures/${adventureId}/recordings/${recordingId}/resume`).then(r => r.data);

export const stopRecording = (adventureId: string, recordingId: string) =>
  api.post<RecordingDto>(`/adventures/${adventureId}/recordings/${recordingId}/stop`).then(r => r.data);

export const getRecordingTranscript = (adventureId: string, recordingId: string) =>
  api.get<TranscriptSegmentDto[]>(`/adventures/${adventureId}/recordings/${recordingId}/transcript`).then(r => r.data);

// ── Adventure transcript (aggregated across recordings, with live updates) ──
export const getAdventureTranscript = (adventureId: string) =>
  api.get<TranscriptSegmentDto[]>(`/adventures/${adventureId}/transcript`).then(r => r.data);

/**
 * Subscribes to live transcript segments for an adventure via Server-Sent
 * Events. Returns an `EventSource` — call `.close()` on it to unsubscribe
 * (e.g. in a `useEffect` cleanup function).
 */
export const subscribeToAdventureTranscript = (
  adventureId: string,
  onSegment: (segment: TranscriptSegmentDto) => void,
) => {
  const source = new EventSource(`/api/adventures/${adventureId}/transcript/stream`);
  source.addEventListener('segment', event => {
    onSegment(JSON.parse((event as MessageEvent).data));
  });
  return source;
};
