export interface ChronicleDto {
  id: string;
  name: string;
  createdAt: string;
}

export interface AdventureDto {
  id: string;
  chronicleId: string;
  name: string;
  status: 'PLANNED' | 'ACTIVE' | 'COMPLETED';
  createdAt: string;
  startedAt: string | null;
  endedAt: string | null;
}

export interface PlayerDto {
  id: string;
  name: string;
}

export interface CharacterDto {
  id: string;
  chronicleId: string;
  name: string;
  playerId: string;
  hasSheet: boolean;
  createdAt: string;
}

export interface AdventureCharacterDto {
  id: string;
  adventureId: string;
  characterId: string;
  addedAt: string;
}

export type NpcStatus = 'HIGHER' | 'EQUAL' | 'LOWER';

export interface NpcDto {
  id: string | null;
  name: string;
  motive: string;
  status: NpcStatus;
  mood: string;
  originChronicleId: string | null;
  createdAt: string | null;
}

export interface AttributePools {
  motives: string[];
  moods: string[];
  statuses: NpcStatus[];
}

// ── Recordings & transcripts ─────────────────────────────────────────────────
export type RecordingSource = 'UPLOAD' | 'MICROPHONE' | 'DISCORD';
export type RecordingStatus = 'RECORDING' | 'PAUSED' | 'STOPPED' | 'PROCESSING' | 'AWAITING_ASR' | 'DONE' | 'FAILED';

export interface RecordingDto {
  id: string;
  chronicleId: string;
  adventureId: string;
  source: RecordingSource;
  status: RecordingStatus;
  startedAt: string;
  endedAt: string | null;
  audioObjectKey: string | null;
  transcriptObjectKey: string | null;
  audioUrl: string | null;
}

export interface TranscriptSegmentDto {
  id: string;
  recordingId: string;
  speakerLabel: string;
  startMs: number;
  endMs: number;
  text: string;
  createdAt: string;
}

// ── Discord bot ───────────────────────────────────────────────────────────────
export interface DiscordGuildDto {
  id: string;
  name: string;
}

export interface DiscordVoiceChannelDto {
  id: string;
  name: string;
  participantCount: number;
}
