export interface SessionDto {
  id: string;
  name: string;
  createdAt: string;
}

export interface PlayerDto {
  id: string;
  name: string;
}

export interface CharacterDto {
  id: string;
  name: string;
  playerId: string;
  hasSheet: boolean;
  createdAt: string;
}

export type NpcStatus = 'HIGHER' | 'EQUAL' | 'LOWER';

export interface NpcDto {
  id: string | null;
  name: string;
  motive: string;
  status: NpcStatus;
  mood: string;
  originSessionId: string | null;
  createdAt: string | null;
}

export interface AttributePools {
  motives: string[];
  moods: string[];
  statuses: NpcStatus[];
}
