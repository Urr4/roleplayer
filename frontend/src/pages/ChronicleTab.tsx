import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import axios from 'axios';
import {
  Alert,
  Autocomplete,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  Grid,
  IconButton,
  List,
  ListItem,
  ListItemButton,
  ListItemText,
  Stack,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import DeleteIcon from '@mui/icons-material/Delete';
import PictureAsPdfIcon from '@mui/icons-material/PictureAsPdf';
import UploadFileIcon from '@mui/icons-material/UploadFile';
import CloseIcon from '@mui/icons-material/Close';
import FiberManualRecordIcon from '@mui/icons-material/FiberManualRecord';
import MicIcon from '@mui/icons-material/Mic';
import DiscordIcon from '@mui/icons-material/Forum';
import PauseIcon from '@mui/icons-material/Pause';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import PlayCircleIcon from '@mui/icons-material/PlayCircle';
import StopIcon from '@mui/icons-material/Stop';
import StopCircleIcon from '@mui/icons-material/StopCircle';
import ExploreIcon from '@mui/icons-material/Explore';
import TranscriptPanel from './TranscriptPanel';
import type { AdventureCharacterDto, AdventureDto, CharacterDto, ChronicleDto, DiscordGuildDto, DiscordVoiceChannelDto, PlayerDto, RecordingDto, TranscriptSegmentDto, WorldDto } from '../types';
import {
  addAdventureCharacter,
  appendRecordingChunk,
  createAdventure,
  createCharacter,
  createChronicle,
  createWorld,
  deleteAdventure,
  deleteCharacter,
  deleteChronicle,
  deleteRecording,
  deleteRecordingTranscript,
  getAdventureCharacters,
  getAdventures,
  getAllCharacters,
  getCharacterSheetUrl,
  getChronicleCharacters,
  getDiscordGuilds,
  getDiscordVoiceChannels,
  getPlayers,
  getRecordings,
  getRecordingTranscript,
  getWorlds,
  importCharacterIntoChronicle,
  pauseRecording,
  pushWorldFacts,
  removeAdventureCharacter,
  replaceCharacterSheet,
  resumeRecording,
  startAdventure,
  startRecording,
  stopAdventure,
  stopRecording,
  uploadRecording,
} from '../api/client';

interface Props {
  chronicles: ChronicleDto[];
  activeChronicleId: string | null;
  onSelectChronicle: (id: string) => void;
  onChroniclesChanged: () => void;
  onActiveAdventureChanged: (adventure: AdventureDto | null) => void;
}

function recordingSourceLabel(source: RecordingDto['source']): string {
  switch (source) {
    case 'UPLOAD':
      return 'Uploaded file';
    case 'MICROPHONE':
      return 'Microphone';
    case 'DISCORD':
      return 'Discord';
    default:
      return source;
  }
}

function recordingStatusLabel(status: RecordingDto['status']): string {
  switch (status) {
    case 'RECORDING':
      return 'Recording';
    case 'PAUSED':
      return 'Paused';
    case 'STOPPED':
      return 'Stopped';
    case 'PROCESSING':
      return 'Transcribing…';
    case 'AWAITING_ASR':
      return 'Waiting for ASR service';
    case 'DONE':
      return 'Transcribed';
    case 'FAILED':
      return 'Failed';
    default:
      return status;
  }
}

function formatRecordingTimeRange(startedAt: string, endedAt: string | null): string {
  const start = new Date(startedAt);
  const startLabel = Number.isNaN(start.getTime()) ? startedAt : start.toLocaleString();
  if (!endedAt) {
    return `${startLabel} – ongoing`;
  }
  const end = new Date(endedAt);
  const endLabel = Number.isNaN(end.getTime()) ? endedAt : end.toLocaleTimeString();
  return `${startLabel} – ${endLabel}`;
}

export default function ChronicleTab({
  chronicles,
  activeChronicleId,
  onSelectChronicle,
  onChroniclesChanged,
  onActiveAdventureChanged,
}: Props) {
  const [newChronicleName, setNewChronicleName] = useState('');
  const [worlds, setWorlds] = useState<WorldDto[]>([]);
  const [selectedWorld, setSelectedWorld] = useState<WorldDto | string | null>(null);

  const [allPlayers, setAllPlayers] = useState<PlayerDto[]>([]);
  const [allCharacters, setAllCharacters] = useState<CharacterDto[]>([]);
  const [chronicleCharacters, setChronicleCharacters] = useState<CharacterDto[]>([]);
  const [selectedPlayer, setSelectedPlayer] = useState<PlayerDto | null>(null);
  const [newCharacterName, setNewCharacterName] = useState('');
  const [newCharacterSheet, setNewCharacterSheet] = useState<File | null>(null);
  const [importCharacter, setImportCharacter] = useState<CharacterDto | null>(null);
  const [viewerUrl, setViewerUrl] = useState<string | null>(null);
  const [viewerTitle, setViewerTitle] = useState('');

  const [adventures, setAdventures] = useState<AdventureDto[]>([]);
  const [expandedAdventureId, setExpandedAdventureId] = useState<string | null>(null);
  const [adventureCharacters, setAdventureCharacters] = useState<Record<string, AdventureCharacterDto[]>>({});
  const [newAdventureName, setNewAdventureName] = useState('');
  const [participantToAdd, setParticipantToAdd] = useState<CharacterDto | null>(null);
  const [addingParticipant, setAddingParticipant] = useState(false);

  const [error, setError] = useState<string | null>(null);

  const [factsDraftText, setFactsDraftText] = useState<Record<string, string>>({});
  const [pushingFactsAdventureId, setPushingFactsAdventureId] = useState<string | null>(null);
  const [factsPushError, setFactsPushError] = useState<Record<string, string>>({});

  const [recordDialogOpen, setRecordDialogOpen] = useState(false);
  const [recordDialogAdventureId, setRecordDialogAdventureId] = useState<string | null>(null);
  const [fullTranscriptOpen, setFullTranscriptOpen] = useState(false);
  const [recordPromptAdventureId, setRecordPromptAdventureId] = useState<string | null>(null);
  const [discordGuilds, setDiscordGuilds] = useState<DiscordGuildDto[]>([]);
  const [discordGuildsLoading, setDiscordGuildsLoading] = useState(false);
  const [selectedDiscordGuild, setSelectedDiscordGuild] = useState<DiscordGuildDto | null>(null);
  const [discordChannels, setDiscordChannels] = useState<DiscordVoiceChannelDto[]>([]);
  const [discordChannelsLoading, setDiscordChannelsLoading] = useState(false);
  const [selectedDiscordChannel, setSelectedDiscordChannel] = useState<DiscordVoiceChannelDto | null>(null);
  const [discordConfirmOpen, setDiscordConfirmOpen] = useState(false);
  const [liveRecording, setLiveRecording] = useState<RecordingDto | null>(null);
  const [liveRecordingAdventureId, setLiveRecordingAdventureId] = useState<string | null>(null);
  const [adventureRecordings, setAdventureRecordings] = useState<RecordingDto[]>([]);
  const [recordingBusy, setRecordingBusy] = useState(false);
  const [expandedRecordingId, setExpandedRecordingId] = useState<string | null>(null);
  const [recordingTranscripts, setRecordingTranscripts] = useState<Record<string, TranscriptSegmentDto[]>>({});
  const [recordingTranscriptsLoading, setRecordingTranscriptsLoading] = useState<Record<string, boolean>>({});
  const [confirmDelete, setConfirmDelete] = useState<
    | { kind: 'chronicle'; id: string; label: string }
    | { kind: 'character'; id: string; label: string }
    | { kind: 'adventure'; id: string; label: string }
    | { kind: 'recording'; id: string; adventureId: string; label: string }
    | { kind: 'transcript'; id: string; adventureId: string; label: string }
    | null
  >(null);
  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const micStreamRef = useRef<MediaStream | null>(null);
  const pendingChunkUploadsRef = useRef<Promise<void>[]>([]);

  const activeChronicle = useMemo(
    () => chronicles.find(chronicle => chronicle.id === activeChronicleId) ?? null,
    [chronicles, activeChronicleId],
  );

  const activeAdventure = useMemo(() => adventures.find(adventure => adventure.status === 'ACTIVE') ?? null, [adventures]);

  useEffect(() => {
    onActiveAdventureChanged(activeAdventure);
  }, [activeAdventure, onActiveAdventureChanged]);

  const playerName = useCallback(
    (playerId: string) => allPlayers.find(player => player.id === playerId)?.name ?? 'Unknown player',
    [allPlayers],
  );

  const releaseMicrophone = useCallback(() => {
    try {
      micStreamRef.current?.getTracks().forEach(track => track.stop());
    } catch {
      // no-op: best-effort cleanup for browsers with transient track errors
    }
    micStreamRef.current = null;
    mediaRecorderRef.current = null;
    pendingChunkUploadsRef.current = [];
  }, []);

  const getRecordingBlockMessage = useCallback(
    (adventureId: string | null) => {
      if (!liveRecordingAdventureId || liveRecordingAdventureId === adventureId) return null;
      const adventureName = adventures.find(adventure => adventure.id === liveRecordingAdventureId)?.name ?? 'another adventure';
      return `Stop the recording on adventure ${adventureName} first.`;
    },
    [liveRecordingAdventureId, adventures],
  );

  const fetchRecordingState = useCallback(
    async (adventureId: string | null) => {
      if (!adventureId) {
        return { liveRecordingAdventureId: null, liveRecording: null as RecordingDto | null, recordings: [] as RecordingDto[] };
      }

      try {
        const recordings = await getRecordings(adventureId);
        const inProgress = recordings.find(recording => recording.status === 'RECORDING' || recording.status === 'PAUSED');
        return {
          liveRecordingAdventureId: inProgress ? adventureId : null,
          liveRecording: inProgress ?? null,
          recordings,
        };
      } catch {
        return { liveRecordingAdventureId: null, liveRecording: null as RecordingDto | null, recordings: [] as RecordingDto[] };
      }
    },
    [],
  );

  const refreshRoster = useCallback(async () => {
    const [players, characters] = await Promise.all([getPlayers(), getAllCharacters()]);
    setAllPlayers(players);
    setAllCharacters(characters);
  }, []);

  const refreshChronicleCharacters = useCallback(async () => {
    if (!activeChronicleId) {
      setChronicleCharacters([]);
      return;
    }
    const characters = await getChronicleCharacters(activeChronicleId);
    setChronicleCharacters(characters);
  }, [activeChronicleId]);

  const refreshAdventures = useCallback(async () => {
    if (!activeChronicleId) {
      setAdventures([]);
      setAdventureCharacters({});
      return;
    }

    const loadedAdventures = await getAdventures(activeChronicleId);
    setAdventures(loadedAdventures);
    setExpandedAdventureId(current => {
      if (current && loadedAdventures.some(adventure => adventure.id === current)) return current;
      return loadedAdventures[0]?.id ?? null;
    });
  }, [activeChronicleId]);

  const refreshAdventureCharacters = useCallback(async (adventureId: string) => {
    const loaded = await getAdventureCharacters(adventureId);
    setAdventureCharacters(previous => ({ ...previous, [adventureId]: loaded }));
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void refreshRoster().catch(err => setError(err instanceof Error ? err.message : 'Unable to load players and characters.'));
    void getWorlds().then(setWorlds).catch(err => setError(err instanceof Error ? err.message : 'Welten konnten nicht geladen werden.'));
  }, [refreshRoster]);

  useEffect(() => {
    let cancelled = false;
    void fetchRecordingState(expandedAdventureId).then(({ liveRecordingAdventureId: currentLiveRecordingAdventureId, liveRecording: currentLiveRecording, recordings }) => {
      if (cancelled) return;
      setLiveRecordingAdventureId(currentLiveRecordingAdventureId);
      setLiveRecording(currentLiveRecording);
      setAdventureRecordings(recordings);
    });

    return () => {
      cancelled = true;
    };
  }, [fetchRecordingState, expandedAdventureId]);

  // Poll periodically so backend-side state changes (e.g. an upload finishing
  // processing, a recording being reconciled to FAILED after a server
  // restart, or a live recording flushed by the scheduler) are reflected in
  // the UI even if the user doesn't take any action.
  useEffect(() => {
    if (!expandedAdventureId) return;
    const interval = window.setInterval(() => {
      void fetchRecordingState(expandedAdventureId).then(({ liveRecordingAdventureId: currentLiveRecordingAdventureId, liveRecording: currentLiveRecording, recordings }) => {
        // If the recording we were tracking as "live" just transitioned to
        // FAILED (e.g. the Discord bot lost the voice connection in the
        // background), surface the reason instead of silently clearing the
        // state and leaving the user with no explanation.
        setLiveRecording(previousLiveRecording => {
          if (previousLiveRecording && !currentLiveRecording) {
            const failedRecording = recordings.find(recording => recording.id === previousLiveRecording.id
                && recording.status === 'FAILED');
            if (failedRecording) {
              setError(failedRecording.errorMessage ?? 'The recording failed unexpectedly. Please check the logs.');
            }
          }
          return currentLiveRecording;
        });
        setLiveRecordingAdventureId(currentLiveRecordingAdventureId);
        setAdventureRecordings(recordings);
      });
    }, 5000);
    return () => window.clearInterval(interval);
  }, [expandedAdventureId, fetchRecordingState]);

  // Seed the editable "world facts" textarea once a draft becomes available
  // from the backend (phase 1 finished, or an empty draft for adventures
  // without recordings) - but only the first time we see it for a given
  // adventure, so we never clobber text the user is actively editing.
  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setFactsDraftText(previous => {
      let changed = false;
      const next = { ...previous };
      for (const adventure of adventures) {
        const hasDraftStatus = adventure.worldExtractionStatus === 'DRAFT_READY'
          || adventure.worldExtractionStatus === 'FAILED'
          || adventure.worldExtractionStatus === 'PUSHING'
          || adventure.worldExtractionStatus === 'DONE';
        if (hasDraftStatus && next[adventure.id] === undefined) {
          next[adventure.id] = adventure.draftFactsText ?? '';
          changed = true;
        }
      }
      return changed ? next : previous;
    });
  }, [adventures]);

  // Poll while any adventure is still waiting on transcription/phase-1 LLM
  // extraction, so the spinner ("Waiting on facts") automatically switches to
  // the editable textarea once the draft becomes available.
  useEffect(() => {
    const hasPending = adventures.some(adventure => adventure.worldExtractionStatus === 'PENDING');
    if (!hasPending) return;
    const interval = window.setInterval(() => {
      void refreshAdventures();
    }, 5000);
    return () => window.clearInterval(interval);
  }, [adventures, refreshAdventures]);


  useEffect(
    () => () => {
      releaseMicrophone();
    },
    [releaseMicrophone],
  );

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void refreshChronicleCharacters().catch(err =>
      setError(err instanceof Error ? err.message : 'Unable to load chronicle characters.'),
    );
    void refreshAdventures().catch(err => setError(err instanceof Error ? err.message : 'Unable to load adventures.'));
  }, [refreshAdventures, refreshChronicleCharacters]);

  useEffect(() => {
    if (!expandedAdventureId) return;
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void refreshAdventureCharacters(expandedAdventureId).catch(err =>
      setError(err instanceof Error ? err.message : 'Unable to load adventure participants.'),
    );
  }, [expandedAdventureId, refreshAdventureCharacters]);

  const importableCharacters = useMemo(
    () =>
      allCharacters.filter(
        character => character.chronicleId !== activeChronicleId && !chronicleCharacters.some(linked => linked.id === character.id),
      ),
    [activeChronicleId, allCharacters, chronicleCharacters],
  );

  const currentAdventureParticipants = useMemo(
    () => (expandedAdventureId ? adventureCharacters[expandedAdventureId] ?? [] : []),
    [adventureCharacters, expandedAdventureId],
  );

  const availableParticipants = useMemo(
    () =>
      chronicleCharacters.filter(
        character => !currentAdventureParticipants.some(participant => participant.characterId === character.id),
      ),
    [chronicleCharacters, currentAdventureParticipants],
  );

  const characterName = useCallback(
    (characterId: string) => chronicleCharacters.find(character => character.id === characterId)?.name ?? 'Unknown character',
    [chronicleCharacters],
  );

  const handleCreateChronicle = async () => {
    const name = newChronicleName.trim();
    const worldInput = typeof selectedWorld === 'string' ? selectedWorld.trim() : selectedWorld?.name?.trim();
    if (!name || !worldInput) return;
    try {
      let worldId = typeof selectedWorld === 'string' || !selectedWorld
        ? (await createWorld(worldInput)).id
        : selectedWorld.id;
      if (typeof selectedWorld !== 'string') {
        const existing = worlds.find(world => world.name.toLowerCase() === worldInput.toLowerCase());
        if (existing) worldId = existing.id;
      }
      const chronicle = await createChronicle(name, worldId);
      setNewChronicleName('');
      setSelectedWorld(null);
      setWorlds(await getWorlds());
      onSelectChronicle(chronicle.id);
      onChroniclesChanged();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Chronik konnte nicht erstellt werden.');
    }
  };

  const handleCreateCharacter = async () => {
    if (!activeChronicleId || !selectedPlayer || !newCharacterName.trim()) return;
    try {
      await createCharacter(activeChronicleId, newCharacterName.trim(), selectedPlayer.id, newCharacterSheet ?? undefined);
      setNewCharacterName('');
      setNewCharacterSheet(null);
      await Promise.all([refreshRoster(), refreshChronicleCharacters()]);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to create character.');
    }
  };

  const handleImportCharacter = async () => {
    if (!activeChronicleId || !importCharacter) return;
    try {
      await importCharacterIntoChronicle(activeChronicleId, importCharacter.id);
      setImportCharacter(null);
      await Promise.all([refreshRoster(), refreshChronicleCharacters()]);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to import character.');
    }
  };

  const handleUploadSheet = async (characterId: string, file: File) => {
    try {
      await replaceCharacterSheet(characterId, file);
      await Promise.all([refreshRoster(), refreshChronicleCharacters()]);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to upload character sheet.');
    }
  };

  const handleViewSheet = async (character: CharacterDto) => {
    try {
      const url = await getCharacterSheetUrl(character.id);
      setViewerUrl(url);
      setViewerTitle(character.name);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to open character sheet.');
    }
  };

  const handleSelectAdventure = (adventureId: string) => {
    setExpandedAdventureId(adventureId);
    setAddingParticipant(false);
    setParticipantToAdd(null);
  };

  const handleCreateAdventure = async () => {
    if (!activeChronicleId || !newAdventureName.trim()) return;
    try {
      const previousAdventure = adventures[adventures.length - 1];
      const adventure = await createAdventure(activeChronicleId, newAdventureName.trim());
      if (previousAdventure) {
        const previousParticipants = await getAdventureCharacters(previousAdventure.id);
        await Promise.all(
          previousParticipants.map(participant => addAdventureCharacter(adventure.id, participant.characterId)),
        );
      }
      setNewAdventureName('');
      await refreshAdventures();
      handleSelectAdventure(adventure.id);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to create adventure.');
    }
  };

  const handleStartAdventure = async (adventureId: string) => {
    try {
      await startAdventure(adventureId);
      await refreshAdventures();
      handleSelectAdventure(adventureId);
      setRecordPromptAdventureId(adventureId);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to start adventure.');
    }
  };

  const handleStopAdventure = async (adventureId: string) => {
    try {
      await stopAdventure(adventureId);
      await refreshAdventures();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to stop adventure.');
    }
  };

  const handleAddFactsToWorld = async (adventureId: string) => {
    const factsText = factsDraftText[adventureId] ?? '';
    setPushingFactsAdventureId(adventureId);
    setFactsPushError(previous => {
      const rest = { ...previous };
      delete rest[adventureId];
      return rest;
    });
    try {
      await pushWorldFacts(adventureId, factsText);
      await refreshAdventures();
    } catch (err) {
      const message = axios.isAxiosError(err)
        ? err.response?.data?.message ?? err.message
        : err instanceof Error
          ? err.message
          : 'Add facts to world failed.';
      setFactsPushError(previous => ({ ...previous, [adventureId]: message }));
      await refreshAdventures();
    } finally {
      setPushingFactsAdventureId(null);
    }
  };

  const handleAddParticipant = async () => {
    if (!expandedAdventureId || !participantToAdd) return;
    try {
      await addAdventureCharacter(expandedAdventureId, participantToAdd.id);
      setParticipantToAdd(null);
      await refreshAdventureCharacters(expandedAdventureId);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to add character to adventure.');
    }
  };

  const handleRemoveParticipant = async (characterId: string) => {
    if (!expandedAdventureId) return;
    try {
      await removeAdventureCharacter(expandedAdventureId, characterId);
      await refreshAdventureCharacters(expandedAdventureId);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to remove character from adventure.');
    }
  };

  const handleToggleRecording = (recordingId: string) => {
    setExpandedRecordingId(current => {
      const next = current === recordingId ? null : recordingId;
      if (next && !recordingTranscripts[next] && expandedAdventureId) {
        setRecordingTranscriptsLoading(prev => ({ ...prev, [next]: true }));
        getRecordingTranscript(expandedAdventureId, next)
          .then(segments => setRecordingTranscripts(prev => ({ ...prev, [next]: segments })))
          .catch(err => setError(err instanceof Error ? err.message : 'Unable to load transcript.'))
          .finally(() => setRecordingTranscriptsLoading(prev => ({ ...prev, [next]: false })));
      }
      return next;
    });
  };

  const handleConfirmDelete = async () => {
    if (!confirmDelete) return;
    try {
      switch (confirmDelete.kind) {
        case 'chronicle':
          await deleteChronicle(confirmDelete.id);
          onChroniclesChanged();
          if (confirmDelete.id === activeChronicleId) onSelectChronicle('');
          break;
        case 'character':
          await deleteCharacter(confirmDelete.id);
          await Promise.all([refreshRoster(), refreshChronicleCharacters()]);
          break;
        case 'adventure':
          await deleteAdventure(confirmDelete.id);
          if (confirmDelete.id === expandedAdventureId) setExpandedAdventureId(null);
          await refreshAdventures();
          break;
        case 'recording':
          await deleteRecording(confirmDelete.adventureId, confirmDelete.id);
          if (confirmDelete.id === expandedRecordingId) setExpandedRecordingId(null);
          if (confirmDelete.adventureId === expandedAdventureId) {
            const state = await fetchRecordingState(expandedAdventureId);
            setAdventureRecordings(state.recordings);
          }
          break;
        case 'transcript':
          await deleteRecordingTranscript(confirmDelete.adventureId, confirmDelete.id);
          setRecordingTranscripts(prev => ({ ...prev, [confirmDelete.id]: [] }));
          if (confirmDelete.adventureId === expandedAdventureId) {
            const state = await fetchRecordingState(expandedAdventureId);
            setAdventureRecordings(state.recordings);
          }
          break;
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to delete.');
    } finally {
      setConfirmDelete(null);
    }
  };

  const handleUploadRecording = async (file: File) => {
    const adventureId = recordDialogAdventureId;
    if (!adventureId) return;
    setRecordDialogOpen(false);
    setRecordDialogAdventureId(null);
    try {
      await uploadRecording(adventureId, file);
      const state = await fetchRecordingState(adventureId);
      setLiveRecordingAdventureId(state.liveRecordingAdventureId);
      setLiveRecording(state.liveRecording);
      setAdventureRecordings(state.recordings);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to upload recording.');
    }
  };

  const handleStartMicrophone = async () => {
    const adventureId = recordDialogAdventureId;
    if (!adventureId || recordingBusy) return;
    const blockMessage = getRecordingBlockMessage(adventureId);
    if (blockMessage) {
      setError(blockMessage);
      return;
    }

    setRecordingBusy(true);
    let stream: MediaStream | null = null;
    let recording: RecordingDto | null = null;

    try {
      stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      recording = await startRecording(adventureId, 'MICROPHONE');
      micStreamRef.current = stream;
      const recorder = new MediaRecorder(stream, { mimeType: 'audio/webm;codecs=opus' });
      const recordingId = recording.id;
      recorder.ondataavailable = event => {
        if (event.data.size > 0) {
          const uploadPromise = appendRecordingChunk(adventureId, recordingId, event.data).then(() => undefined);
          pendingChunkUploadsRef.current = [...pendingChunkUploadsRef.current, uploadPromise];
          void uploadPromise.finally(() => {
            pendingChunkUploadsRef.current = pendingChunkUploadsRef.current.filter(promise => promise !== uploadPromise);
          });
        }
      };
      recorder.start(10000);
      mediaRecorderRef.current = recorder;
      setLiveRecording(recording);
      setLiveRecordingAdventureId(adventureId);
      setRecordDialogOpen(false);
      setRecordDialogAdventureId(null);
    } catch (err) {
      try {
        stream?.getTracks().forEach(track => track.stop());
      } catch {
        // ignore cleanup errors
      }
      if (recording) {
        await stopRecording(adventureId, recording.id).catch(() => undefined);
      }
      releaseMicrophone();
      setError(err instanceof Error ? err.message : 'Unable to start microphone recording.');
    } finally {
      setRecordingBusy(false);
    }
  };

  const handleRequestStartDiscord = () => {
    if (!selectedDiscordGuild || !selectedDiscordChannel) return;
    setDiscordConfirmOpen(true);
  };

  const handleStartDiscord = async () => {
    const adventureId = recordDialogAdventureId;
    if (!adventureId || !selectedDiscordChannel || recordingBusy) return;
    const blockMessage = getRecordingBlockMessage(adventureId);
    if (blockMessage) {
      setError(blockMessage);
      setDiscordConfirmOpen(false);
      return;
    }

    setRecordingBusy(true);
    try {
      const recording = await startRecording(adventureId, 'DISCORD', selectedDiscordChannel.id);
      setLiveRecording(recording);
      setLiveRecordingAdventureId(adventureId);
      setRecordDialogOpen(false);
      setRecordDialogAdventureId(null);
      resetDiscordPicker();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to start Discord recording.');
    } finally {
      setRecordingBusy(false);
    }
  };

  const handlePauseOrContinue = async () => {
    if (!liveRecordingAdventureId || !liveRecording || recordingBusy) return;
    setRecordingBusy(true);
    try {
      if (liveRecording.status === 'RECORDING') {
        const recorder = mediaRecorderRef.current;
        if (recorder && recorder.state === 'recording') {
          // MediaRecorder.pause() does not emit a dataavailable event, so
          // whatever audio has been captured since the last automatic
          // 10s timeslice tick would otherwise be silently dropped from the
          // final stored file every time the recording is paused (this is
          // what made playback duration end up shorter than the actual
          // recorded time). requestData() forces an immediate flush of that
          // pending audio before we actually pause and upload it like any
          // other chunk.
          await new Promise<void>(resolve => {
            recorder.addEventListener('dataavailable', () => resolve(), { once: true });
            recorder.requestData();
          });
          await Promise.all(pendingChunkUploadsRef.current);
          recorder.pause();
        }
        const updated = await pauseRecording(liveRecordingAdventureId, liveRecording.id);
        setLiveRecording(updated);
      } else {
        mediaRecorderRef.current?.resume();
        const updated = await resumeRecording(liveRecordingAdventureId, liveRecording.id);
        setLiveRecording(updated);
      }
    } catch (err) {
      if (axios.isAxiosError(err) && (err.response?.status === 409 || err.response?.status === 404)) {
        // The recording is no longer tracked on the server (e.g. it was
        // orphaned by a backend restart). Clear the stuck state locally so
        // the user can start a new recording instead of the buttons being
        // permanently unresponsive.
        setLiveRecording(null);
        setLiveRecordingAdventureId(null);
        setError('This recording is no longer active on the server (it may have been interrupted by a restart). You can start a new recording.');
      } else {
        setError(err instanceof Error ? err.message : 'Unable to update the recording state.');
      }
    } finally {
      setRecordingBusy(false);
    }
  };

  const handleStopRecording = async () => {
    if (!liveRecordingAdventureId || !liveRecording || recordingBusy) return;
    setRecordingBusy(true);
    const recorder = mediaRecorderRef.current;
    const adventureId = liveRecordingAdventureId;
    try {
      if (recorder) {
        if (recorder.state !== 'inactive') {
          await new Promise<void>((resolve, reject) => {
            recorder.addEventListener('stop', () => resolve(), { once: true });
            try {
              recorder.stop();
            } catch (err) {
              reject(err);
            }
          });
        }
        await Promise.all(pendingChunkUploadsRef.current);
        releaseMicrophone();
      }

      await stopRecording(adventureId, liveRecording.id);
      setLiveRecording(null);
      setLiveRecordingAdventureId(current => (current === adventureId ? null : current));
    } catch (err) {
      if (axios.isAxiosError(err) && (err.response?.status === 409 || err.response?.status === 404)) {
        setLiveRecording(null);
        setLiveRecordingAdventureId(current => (current === adventureId ? null : current));
        setError('This recording is no longer active on the server (it may have been interrupted by a restart).');
      } else {
        setError(err instanceof Error ? err.message : 'Unable to stop the recording.');
      }
    } finally {
      releaseMicrophone();
      setRecordingBusy(false);
    }
  };

  const resetDiscordPicker = useCallback(() => {
    setDiscordGuilds([]);
    setSelectedDiscordGuild(null);
    setDiscordChannels([]);
    setSelectedDiscordChannel(null);
    setDiscordConfirmOpen(false);
  }, []);

  const openRecordDialog = (adventureId: string) => {
    setRecordDialogAdventureId(adventureId);
    setRecordDialogOpen(true);
    resetDiscordPicker();
    setDiscordGuildsLoading(true);
    getDiscordGuilds()
      .then(guilds => setDiscordGuilds(guilds))
      .catch(err => setError(err instanceof Error ? err.message : 'Unable to load Discord servers.'))
      .finally(() => setDiscordGuildsLoading(false));
  };

  const handleSelectDiscordGuild = (guild: DiscordGuildDto | null) => {
    setSelectedDiscordGuild(guild);
    setDiscordChannels([]);
    setSelectedDiscordChannel(null);
    if (!guild) return;
    setDiscordChannelsLoading(true);
    getDiscordVoiceChannels(guild.id)
      .then(channels => setDiscordChannels(channels))
      .catch(err => setError(err instanceof Error ? err.message : 'Unable to load Discord voice channels.'))
      .finally(() => setDiscordChannelsLoading(false));
  };

  return (
    <Box>
      {error && (
        <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError(null)}>
          {error}
        </Alert>
      )}

      <Grid container spacing={3}>
        <Grid size={{ xs: 12, lg: 4 }}>
          <Card>
            <CardContent>
              <Stack spacing={2}>
                <Typography variant="h5">Chronicles</Typography>
                <Stack spacing={1}>
                  <TextField
                    label="Name der Chronik"
                    size="small"
                    fullWidth
                    value={newChronicleName}
                    onChange={event => setNewChronicleName(event.target.value)}
                    onKeyDown={event => event.key === 'Enter' && void handleCreateChronicle()}
                  />
                  <Autocomplete
                    freeSolo
                    options={worlds}
                    getOptionLabel={option => typeof option === 'string' ? option : option.name}
                    value={selectedWorld}
                    onChange={(_, value) => setSelectedWorld(value)}
                    onInputChange={(_, value, reason) => {
                      if (reason === 'input') setSelectedWorld(value);
                    }}
                    renderInput={params => <TextField {...params} label="Welt auswählen oder neu anlegen" size="small" />}
                  />
                  <Button variant="contained" startIcon={<AddIcon />} onClick={() => void handleCreateChronicle()} disabled={!newChronicleName.trim() || !(typeof selectedWorld === 'string' ? selectedWorld.trim() : selectedWorld?.name)}>
                    Erstellen
                  </Button>
                </Stack>

                <List sx={{ py: 0 }}>
                  {chronicles.map(chronicle => (
                    <ListItem
                      key={chronicle.id}
                      disablePadding
                      secondaryAction={
                        <Tooltip title="Delete chronicle (and all its adventures, characters, recordings)">
                          <IconButton
                            edge="end"
                            size="small"
                            color="error"
                            onClick={event => {
                              event.stopPropagation();
                              setConfirmDelete({ kind: 'chronicle', id: chronicle.id, label: chronicle.name });
                            }}
                          >
                            <DeleteIcon fontSize="small" />
                          </IconButton>
                        </Tooltip>
                      }
                      sx={{ borderRadius: 2, mb: 0.5 }}
                    >
                      <ListItemButton
                        selected={chronicle.id === activeChronicleId}
                        onClick={() => onSelectChronicle(chronicle.id)}
                        sx={{ borderRadius: 2 }}
                      >
                        <ListItemText primary={chronicle.name} secondary={`${new Date(chronicle.createdAt).toLocaleString()}${chronicle.worldName ? ` · Welt: ${chronicle.worldName}` : ''}`} />
                      </ListItemButton>
                    </ListItem>
                  ))}
                  {chronicles.length === 0 && (
                    <Typography color="text.secondary" sx={{ fontStyle: 'italic' }}>
                      No chronicles yet — create your first one here.
                    </Typography>
                  )}
                </List>
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid size={{ xs: 12, lg: 8 }}>
          {!activeChronicle ? (
            <Card>
              <CardContent>
                <Typography color="text.secondary">Select or create a chronicle to manage characters, adventures, and recordings.</Typography>
              </CardContent>
            </Card>
          ) : (
            <Stack spacing={3}>
              <Card>
                <CardContent>
                  <Stack spacing={2}>
                    <Box>
                      <Typography variant="h5">Characters</Typography>
                      <Typography color="text.secondary">Create or import chronicle-scoped characters.</Typography>
                    </Box>

                    <Grid container spacing={2}>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <Stack spacing={1.5}>
                          <TextField
                            label="Character name"
                            size="small"
                            value={newCharacterName}
                            onChange={event => setNewCharacterName(event.target.value)}
                          />
                          <Autocomplete
                            options={allPlayers}
                            getOptionLabel={player => player.name}
                            value={selectedPlayer}
                            onChange={(_, value) => setSelectedPlayer(value)}
                            renderInput={params => <TextField {...params} label="Player" size="small" />}
                          />
                          <Button component="label" size="small" startIcon={<UploadFileIcon />} variant="outlined">
                            {newCharacterSheet ? newCharacterSheet.name : 'Attach sheet (PDF)'}
                            <input
                              hidden
                              type="file"
                              accept="application/pdf"
                              onChange={event => setNewCharacterSheet(event.target.files?.[0] ?? null)}
                            />
                          </Button>
                          <Button
                            variant="contained"
                            onClick={() => void handleCreateCharacter()}
                            disabled={!selectedPlayer || !newCharacterName.trim()}
                          >
                            Create character
                          </Button>
                        </Stack>
                      </Grid>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <Stack spacing={1.5}>
                          <Autocomplete
                            options={importableCharacters}
                            getOptionLabel={character => `${character.name} (${playerName(character.playerId)})`}
                            value={importCharacter}
                            onChange={(_, value) => setImportCharacter(value)}
                            renderInput={params => <TextField {...params} label="Import character from another chronicle" size="small" />}
                          />
                          <Button variant="outlined" onClick={() => void handleImportCharacter()} disabled={!importCharacter}>
                            Import into chronicle
                          </Button>
                          <Typography variant="caption" color="text.secondary">
                            Playing the same character in a new chronicle? Import it here instead of recreating it.
                          </Typography>
                        </Stack>
                      </Grid>
                    </Grid>

                    <Divider />

                    <Grid container spacing={2}>
                      {chronicleCharacters.map(character => (
                        <Grid key={character.id} size={{ xs: 12, md: 6 }}>
                          <Card variant="outlined">
                            <CardContent>
                              <Stack direction="row" justifyContent="space-between" alignItems="flex-start" spacing={2}>
                                <Box>
                                  <Typography variant="h6">{character.name}</Typography>
                                  <Typography color="text.secondary">Played by {playerName(character.playerId)}</Typography>
                                </Box>
                                {character.hasSheet ? (
                                  <Button size="small" startIcon={<PictureAsPdfIcon />} onClick={() => void handleViewSheet(character)}>
                                    View sheet
                                  </Button>
                                ) : (
                                  <Button size="small" component="label" startIcon={<UploadFileIcon />}>
                                    Upload sheet
                                    <input
                                      hidden
                                      type="file"
                                      accept="application/pdf"
                                      onChange={event => {
                                        const file = event.target.files?.[0];
                                        if (file) void handleUploadSheet(character.id, file);
                                      }}
                                    />
                                  </Button>
                                )}
                                <Tooltip title="Delete character">
                                  <IconButton
                                    size="small"
                                    color="error"
                                    onClick={() => setConfirmDelete({ kind: 'character', id: character.id, label: character.name })}
                                  >
                                    <DeleteIcon fontSize="small" />
                                  </IconButton>
                                </Tooltip>
                              </Stack>
                            </CardContent>
                          </Card>
                        </Grid>
                      ))}
                      {chronicleCharacters.length === 0 && (
                        <Grid size={12}>
                          <Typography color="text.secondary" sx={{ fontStyle: 'italic' }}>
                            No characters in this chronicle yet.
                          </Typography>
                        </Grid>
                      )}
                    </Grid>
                  </Stack>
                </CardContent>
              </Card>

              <Card>
                <CardContent>
                  <Stack spacing={2}>
                    <Box>
                      <Typography variant="h5">Adventures</Typography>
                      <Typography color="text.secondary">
                        Only one adventure can be active per chronicle. Starting it unlocks Initiative Tracker and NPC Helper for it.
                      </Typography>
                    </Box>

                    <Stack spacing={1.5}>
                      <TextField
                        label="New adventure name"
                        size="small"
                        fullWidth
                        value={newAdventureName}
                        onChange={event => setNewAdventureName(event.target.value)}
                        onKeyDown={event => event.key === 'Enter' && void handleCreateAdventure()}
                      />
                      <Button
                        variant="contained"
                        startIcon={<AddIcon />}
                        onClick={() => void handleCreateAdventure()}
                        disabled={!newAdventureName.trim()}
                        sx={{ alignSelf: 'flex-start' }}
                      >
                        Create adventure
                      </Button>
                    </Stack>

                    <Grid container spacing={2}>
                      <Grid size={{ xs: 12, md: 4 }}>
                        <List sx={{ py: 0 }}>
                          {adventures.map(adventure => (
                            <ListItem
                              key={adventure.id}
                              disablePadding
                              secondaryAction={
                                <Stack direction="row" spacing={0.5} alignItems="center">
                                  {adventure.status === 'ACTIVE' ? (
                                    <Tooltip title="Stop adventure">
                                      <IconButton
                                        color="error"
                                        onClick={event => {
                                          event.stopPropagation();
                                          void handleStopAdventure(adventure.id);
                                        }}
                                      >
                                        <StopCircleIcon />
                                      </IconButton>
                                    </Tooltip>
                                  ) : adventure.status === 'PLANNED' || adventure.status === 'COMPLETED' ? (
                                    <Tooltip
                                      title={
                                        activeAdventure
                                          ? 'Stop the active adventure first'
                                          : adventure.status === 'COMPLETED'
                                            ? 'Continue adventure'
                                            : 'Start adventure'
                                      }
                                    >
                                      <span>
                                        <IconButton
                                          color="success"
                                          disabled={!!activeAdventure && activeAdventure.id !== adventure.id}
                                          onClick={event => {
                                            event.stopPropagation();
                                            void handleStartAdventure(adventure.id);
                                          }}
                                        >
                                          <PlayCircleIcon />
                                        </IconButton>
                                      </span>
                                    </Tooltip>
                                  ) : null}
                                  <Tooltip title="Delete adventure (and its recordings)">
                                    <IconButton
                                      size="small"
                                      color="error"
                                      onClick={event => {
                                        event.stopPropagation();
                                        setConfirmDelete({ kind: 'adventure', id: adventure.id, label: adventure.name });
                                      }}
                                    >
                                      <DeleteIcon fontSize="small" />
                                    </IconButton>
                                  </Tooltip>
                                </Stack>
                              }
                              sx={{ borderRadius: 2, mb: 0.5 }}
                            >
                              <ListItemButton selected={adventure.id === expandedAdventureId} onClick={() => handleSelectAdventure(adventure.id)}>
                                <ListItemText
                                  primary={
                                    <Stack direction="row" spacing={0.75} alignItems="center">
                                      {adventure.status === 'ACTIVE' && <ExploreIcon fontSize="small" color="success" />}
                                      <Typography component="span">{adventure.name}</Typography>
                                      {liveRecordingAdventureId === adventure.id && liveRecording?.status === 'RECORDING' && (
                                        <Tooltip title="Recording in progress">
                                          <FiberManualRecordIcon
                                            fontSize="small"
                                            sx={{ color: 'error.main', animation: 'pulse 1.5s ease-in-out infinite',
                                              '@keyframes pulse': { '0%': { opacity: 1 }, '50%': { opacity: 0.3 }, '100%': { opacity: 1 } } }}
                                          />
                                        </Tooltip>
                                      )}
                                    </Stack>
                                  }
                                  secondary={
                                    <Stack spacing={0.25}>
                                      <Chip
                                        size="small"
                                        label={adventure.status}
                                        color={adventure.status === 'ACTIVE' ? 'success' : adventure.status === 'COMPLETED' ? 'default' : 'warning'}
                                        sx={{ alignSelf: 'flex-start' }}
                                      />
                                      {adventure.startedAt && (
                                        <Typography variant="caption" color="text.secondary" component="span">
                                          Started {new Date(adventure.startedAt).toLocaleString()}
                                          {adventure.endedAt && ` – Ended ${new Date(adventure.endedAt).toLocaleString()}`}
                                        </Typography>
                                      )}
                                      {adventure.worldExtractionStatus === 'PENDING' && (
                                        <Typography variant="caption" color="text.secondary" component="span">Waiting on facts…</Typography>
                                      )}
                                      {adventure.worldExtractionStatus === 'DRAFT_READY' && (
                                        <Typography variant="caption" color="text.secondary" component="span">Weltfakten bereit zum Review</Typography>
                                      )}
                                      {adventure.worldExtractionStatus === 'DONE' && (
                                        <Typography variant="caption" color="success.main" component="span">Weltfakten aktualisiert ✓</Typography>
                                      )}
                                      {adventure.worldExtractionStatus === 'FAILED' && (
                                        <Typography variant="caption" color="error.main" component="span">Push fehlgeschlagen – siehe unten</Typography>
                                      )}
                                    </Stack>
                                  }
                                />
                              </ListItemButton>
                            </ListItem>
                          ))}
                          {adventures.length === 0 && (
                            <Typography color="text.secondary" sx={{ fontStyle: 'italic' }}>
                              No adventures yet for this chronicle.
                            </Typography>
                          )}
                        </List>
                      </Grid>
                      <Grid size={{ xs: 12, md: 8 }}>
                        {!expandedAdventureId ? (
                          <Typography color="text.secondary">Select an adventure to manage participating characters.</Typography>
                        ) : (
                          <Stack spacing={2}>
                            <Typography variant="h6">
                              {adventures.find(adventure => adventure.id === expandedAdventureId)?.name}
                            </Typography>

                            <Box>
                              <Typography variant="subtitle2" color="text.secondary" gutterBottom>
                                Participating characters
                              </Typography>
                              {currentAdventureParticipants.length === 0 ? (
                                <Typography color="text.secondary" sx={{ fontStyle: 'italic', mb: 1 }}>
                                  No characters added to this adventure yet.
                                </Typography>
                              ) : (
                                <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap" sx={{ mb: 1 }}>
                                  {currentAdventureParticipants.map(participant => (
                                    <Chip
                                      key={participant.id}
                                      label={characterName(participant.characterId)}
                                      onDelete={() => void handleRemoveParticipant(participant.characterId)}
                                    />
                                  ))}
                                </Stack>
                              )}

                              <Tooltip title="Add a character to this adventure">
                                <span>
                                  <IconButton
                                    size="small"
                                    color="primary"
                                    disabled={availableParticipants.length === 0}
                                    onClick={() => setAddingParticipant(true)}
                                  >
                                    <AddIcon />
                                  </IconButton>
                                </span>
                              </Tooltip>

                              {addingParticipant && (
                                <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} sx={{ mt: 1 }}>
                                  <Autocomplete
                                    sx={{ flexGrow: 1, minWidth: 220 }}
                                    options={availableParticipants}
                                    getOptionLabel={character => character.name}
                                    value={participantToAdd}
                                    onChange={(_, value) => setParticipantToAdd(value)}
                                    renderInput={params => <TextField {...params} label="Character" size="small" autoFocus />}
                                  />
                                  <Button
                                    variant="contained"
                                    onClick={() => {
                                      void handleAddParticipant();
                                      setAddingParticipant(false);
                                    }}
                                    disabled={!participantToAdd}
                                  >
                                    Add
                                  </Button>
                                  <Button
                                    onClick={() => {
                                      setAddingParticipant(false);
                                      setParticipantToAdd(null);
                                    }}
                                  >
                                    Cancel
                                  </Button>
                                </Stack>
                              )}
                            </Box>

                            <Typography variant="caption" color="text.secondary">
                              If a player's character changes, create a new character for them above and add it here.
                            </Typography>

                            <Divider />

                            {(() => {
                              const expandedAdventure = adventures.find(adventure => adventure.id === expandedAdventureId);
                              if (!expandedAdventure || expandedAdventure.worldExtractionStatus === undefined
                                || expandedAdventure.worldExtractionStatus === 'NONE') {
                                return null;
                              }
                              const status = expandedAdventure.worldExtractionStatus;
                              const isPushing = pushingFactsAdventureId === expandedAdventure.id || status === 'PUSHING';
                              const pushError = factsPushError[expandedAdventure.id];
                              return (
                                <Box>
                                  <Typography variant="subtitle2" color="text.secondary" gutterBottom>
                                    World Facts
                                  </Typography>
                                  {status === 'PENDING' ? (
                                    <Stack direction="row" spacing={1.5} alignItems="center" sx={{ py: 1 }}>
                                      <CircularProgress size={18} />
                                      <Typography color="text.secondary">Waiting on facts</Typography>
                                    </Stack>
                                  ) : (
                                    <Stack spacing={1}>
                                      {status === 'DONE' && (
                                        <Alert severity="success">
                                          Weltfakten aktualisiert ✓
                                          {activeChronicle?.worldSlug
                                            ? ` · https://urr4.github.io/roleplaying-worlds/worlds/${activeChronicle.worldSlug}/`
                                            : ''}
                                        </Alert>
                                      )}
                                      {status === 'FAILED' && (pushError || expandedAdventure.worldExtractionError) && (
                                        <Alert severity="error">{pushError ?? expandedAdventure.worldExtractionError}</Alert>
                                      )}
                                      <TextField
                                        multiline
                                        minRows={6}
                                        maxRows={20}
                                        fullWidth
                                        placeholder="Notizen zu Charakteren, NPCs, der Welt, Kultur, Politik usw. …"
                                        value={factsDraftText[expandedAdventure.id] ?? ''}
                                        disabled={isPushing}
                                        onChange={event =>
                                          setFactsDraftText(previous => ({ ...previous, [expandedAdventure.id]: event.target.value }))
                                        }
                                      />
                                      <Button
                                        variant="contained"
                                        onClick={() => void handleAddFactsToWorld(expandedAdventure.id)}
                                        disabled={isPushing}
                                        startIcon={isPushing ? <CircularProgress size={16} color="inherit" /> : undefined}
                                        sx={{ alignSelf: 'flex-start' }}
                                      >
                                        {isPushing ? 'Pushing…' : 'Add facts to world'}
                                      </Button>
                                    </Stack>
                                  )}
                                </Box>
                              );
                            })()}

                            <Divider />

                            <Box>
                              <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" alignItems={{ sm: 'center' }} spacing={1.5}>
                                <Typography variant="subtitle2" color="text.secondary">
                                  Recording & Transcript
                                </Typography>
                                {liveRecordingAdventureId === expandedAdventureId && liveRecording ? (
                                  <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} alignItems={{ sm: 'center' }}>
                                    <Chip
                                      size="small"
                                      icon={<FiberManualRecordIcon sx={liveRecording.status === 'RECORDING'
                                        ? { color: 'error.main', animation: 'pulse 1.5s ease-in-out infinite', '@keyframes pulse': { '0%': { opacity: 1 }, '50%': { opacity: 0.3 }, '100%': { opacity: 1 } } }
                                        : { color: 'text.disabled' }} />}
                                      label={liveRecording.status === 'RECORDING' ? 'Recording…' : 'Paused'}
                                      color={liveRecording.status === 'RECORDING' ? 'error' : 'default'}
                                      variant={liveRecording.status === 'RECORDING' ? 'filled' : 'outlined'}
                                    />
                                    <Button
                                      variant="outlined"
                                      startIcon={liveRecording.status === 'RECORDING' ? <PauseIcon /> : <PlayArrowIcon />}
                                      onClick={() => void handlePauseOrContinue()}
                                      disabled={recordingBusy}
                                    >
                                      {liveRecording.status === 'RECORDING' ? 'Pause recording' : 'Continue recording'}
                                    </Button>
                                    <Button
                                      variant="contained"
                                      color="error"
                                      startIcon={<StopIcon />}
                                      onClick={() => void handleStopRecording()}
                                      disabled={recordingBusy}
                                    >
                                      Complete recording
                                    </Button>
                                  </Stack>
                                ) : (
                                  <Tooltip title={getRecordingBlockMessage(expandedAdventureId) ?? 'Record audio for this adventure'}>
                                    <span>
                                      <Button
                                        color="error"
                                        variant="contained"
                                        startIcon={<FiberManualRecordIcon />}
                                        disabled={recordingBusy || !!getRecordingBlockMessage(expandedAdventureId)}
                                        onClick={() => openRecordDialog(expandedAdventureId)}
                                      >
                                        Record
                                      </Button>
                                    </span>
                                  </Tooltip>
                                )}
                              </Stack>

                              {adventureRecordings.length > 0 && (
                                <Box sx={{ mt: 2 }}>
                                  <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.5 }}>
                                    Recorded audio files
                                  </Typography>
                                  <Stack spacing={1}>
                                    {adventureRecordings.map(recording => {
                                      const isExpanded = expandedRecordingId === recording.id;
                                      const transcript = recordingTranscripts[recording.id];
                                      const transcriptLoading = !!recordingTranscriptsLoading[recording.id];
                                      return (
                                        <Box
                                          key={recording.id}
                                          sx={{
                                            border: '1px solid',
                                            borderColor: 'divider',
                                            borderRadius: 1,
                                            p: 1,
                                          }}
                                        >
                                          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} alignItems={{ sm: 'center' }} justifyContent="space-between">
                                            <ListItemButton
                                              onClick={() => handleToggleRecording(recording.id)}
                                              sx={{ borderRadius: 1, px: 1, py: 0.5, flexGrow: 1 }}
                                            >
                                              <Typography variant="body2">
                                                {formatRecordingTimeRange(recording.startedAt, recording.endedAt)} ·{' '}
                                                {recordingSourceLabel(recording.source)} · {recordingStatusLabel(recording.status)}
                                              </Typography>
                                            </ListItemButton>
                                            <Tooltip title="Delete recording (audio + transcript)">
                                              <IconButton
                                                size="small"
                                                color="error"
                                                onClick={() =>
                                                  setConfirmDelete({
                                                    kind: 'recording',
                                                    id: recording.id,
                                                    adventureId: recording.adventureId,
                                                    label: formatRecordingTimeRange(recording.startedAt, recording.endedAt),
                                                  })
                                                }
                                              >
                                                <DeleteIcon fontSize="small" />
                                              </IconButton>
                                            </Tooltip>
                                          </Stack>
                                          {isExpanded && (
                                            <Box sx={{ mt: 1 }}>
                                              {recording.audioUrl ? (
                                                <Box component="audio" controls src={recording.audioUrl} sx={{ width: '100%' }} />
                                              ) : (
                                                <Typography variant="caption" color="text.secondary">
                                                  {recording.status === 'AWAITING_ASR'
                                                    ? 'Audio stored — waiting for the ASR service to become reachable to transcribe it.'
                                                    : 'Audio not available yet.'}
                                                </Typography>
                                              )}
                                              {recording.status === 'FAILED' && recording.errorMessage && (
                                                <Alert severity="error" sx={{ mt: 0.5 }}>
                                                  {recording.errorMessage}
                                                </Alert>
                                              )}
                                              <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mt: 1 }}>
                                                <Typography variant="caption" color="text.secondary">
                                                  Transcript
                                                </Typography>
                                                {transcript && transcript.length > 0 && (
                                                  <Tooltip title="Delete transcript only">
                                                    <IconButton
                                                      size="small"
                                                      color="error"
                                                      onClick={() =>
                                                        setConfirmDelete({
                                                          kind: 'transcript',
                                                          id: recording.id,
                                                          adventureId: recording.adventureId,
                                                          label: formatRecordingTimeRange(recording.startedAt, recording.endedAt),
                                                        })
                                                      }
                                                    >
                                                      <DeleteIcon fontSize="small" />
                                                    </IconButton>
                                                  </Tooltip>
                                                )}
                                              </Stack>
                                              {transcriptLoading ? (
                                                <Typography variant="caption" color="text.secondary">
                                                  Loading transcript…
                                                </Typography>
                                              ) : transcript && transcript.length > 0 ? (
                                                <Stack spacing={0.25} sx={{ maxHeight: 240, overflowY: 'auto' }}>
                                                  {transcript.map(segment => (
                                                    <Typography key={segment.id} variant="body2">
                                                      <b>{segment.speakerLabel}:</b> {segment.text}
                                                    </Typography>
                                                  ))}
                                                </Stack>
                                              ) : (
                                                <Typography variant="caption" color="text.secondary" sx={{ fontStyle: 'italic' }}>
                                                  No transcript yet.
                                                </Typography>
                                              )}
                                            </Box>
                                          )}
                                        </Box>
                                      );
                                    })}
                                  </Stack>
                                </Box>
                              )}

                              <Box sx={{ mt: 2 }}>
                                <TranscriptPanel
                                  adventureId={expandedAdventureId}
                                  isLive={
                                    liveRecordingAdventureId === expandedAdventureId &&
                                    (liveRecording?.status === 'RECORDING' || liveRecording?.status === 'PAUSED')
                                  }
                                  onOpenFull={() => setFullTranscriptOpen(true)}
                                />
                              </Box>
                            </Box>
                          </Stack>
                        )}
                      </Grid>
                    </Grid>
                  </Stack>
                </CardContent>
              </Card>
            </Stack>
          )}
        </Grid>
      </Grid>

      <Dialog open={!!confirmDelete} onClose={() => setConfirmDelete(null)} maxWidth="xs" fullWidth>
        <DialogTitle>Delete {confirmDelete?.kind}?</DialogTitle>
        <DialogContent>
          <Typography>
            Are you sure you want to delete "{confirmDelete?.label}"?
            {confirmDelete?.kind === 'chronicle' && ' This also deletes all its adventures, characters, and recordings.'}
            {confirmDelete?.kind === 'adventure' && ' This also deletes all its recordings and transcripts.'}
            {confirmDelete?.kind === 'recording' && ' This deletes the audio file and its transcript.'}
            {confirmDelete?.kind === 'transcript' && ' The audio file itself is kept.'}
            {' '}This cannot be undone.
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setConfirmDelete(null)}>Cancel</Button>
          <Button color="error" variant="contained" onClick={() => void handleConfirmDelete()}>
            Delete
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={!!viewerUrl} onClose={() => setViewerUrl(null)} maxWidth="md" fullWidth>
        <DialogTitle sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          {viewerTitle}'s Character Sheet
          <IconButton onClick={() => setViewerUrl(null)}>
            <CloseIcon />
          </IconButton>
        </DialogTitle>
        <DialogContent sx={{ height: '75vh' }}>{viewerUrl && <embed src={viewerUrl} type="application/pdf" width="100%" height="100%" />}</DialogContent>
      </Dialog>

      <Dialog open={fullTranscriptOpen} onClose={() => setFullTranscriptOpen(false)} maxWidth="md" fullWidth fullScreen>
        <DialogTitle sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          Full Transcript
          <IconButton onClick={() => setFullTranscriptOpen(false)}>
            <CloseIcon />
          </IconButton>
        </DialogTitle>
        <DialogContent>
          {expandedAdventureId && (
            <TranscriptPanel
              adventureId={expandedAdventureId}
              isLive={
                liveRecordingAdventureId === expandedAdventureId &&
                (liveRecording?.status === 'RECORDING' || liveRecording?.status === 'PAUSED')
              }
              variant="full"
            />
          )}
        </DialogContent>
      </Dialog>

      <Dialog
        open={recordDialogOpen}
        onClose={() => {
          setRecordDialogOpen(false);
          setRecordDialogAdventureId(null);
          resetDiscordPicker();
        }}
        maxWidth="xs"
        fullWidth
      >
        <DialogTitle sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          Record Adventure Audio
          <IconButton
            onClick={() => {
              setRecordDialogOpen(false);
              setRecordDialogAdventureId(null);
              resetDiscordPicker();
            }}
          >
            <CloseIcon />
          </IconButton>
        </DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ py: 1 }}>
            {error && recordDialogOpen && (
              <Alert severity="error" onClose={() => setError(null)}>
                {error}
              </Alert>
            )}
            <Button component="label" variant="outlined" startIcon={<UploadFileIcon />} fullWidth>
              Upload file
              <input
                hidden
                type="file"
                accept="audio/*,.mp3,.wav,.m4a,.ogg,.oga,.flac,.webm,.aac"
                onChange={event => {
                  const file = event.target.files?.[0];
                  if (file) void handleUploadRecording(file);
                  event.target.value = '';
                }}
              />
            </Button>
            <Button
              variant="outlined"
              startIcon={<MicIcon />}
              fullWidth
              disabled={recordingBusy}
              onClick={() => void handleStartMicrophone()}
            >
              Microphone
            </Button>
            <Divider>Discord</Divider>
            <Autocomplete
              options={discordGuilds}
              getOptionLabel={guild => guild.name}
              value={selectedDiscordGuild}
              loading={discordGuildsLoading}
              onChange={(_, guild) => handleSelectDiscordGuild(guild)}
              renderInput={params => <TextField {...params} label="Server" size="small" />}
              noOptionsText={discordGuildsLoading ? 'Loading…' : 'No Discord servers found'}
            />
            <Autocomplete
              options={discordChannels}
              getOptionLabel={channel => `${channel.name} (${channel.participantCount})`}
              value={selectedDiscordChannel}
              loading={discordChannelsLoading}
              disabled={!selectedDiscordGuild}
              onChange={(_, channel) => setSelectedDiscordChannel(channel)}
              renderInput={params => <TextField {...params} label="Voice channel" size="small" />}
              noOptionsText={discordChannelsLoading ? 'Loading…' : 'No occupied voice channels'}
            />
            <Button
              variant="outlined"
              startIcon={<DiscordIcon />}
              fullWidth
              disabled={recordingBusy || !selectedDiscordGuild || !selectedDiscordChannel}
              onClick={handleRequestStartDiscord}
            >
              Discord
            </Button>
          </Stack>
        </DialogContent>
      </Dialog>

      <Dialog open={discordConfirmOpen} onClose={() => setDiscordConfirmOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle>Start recording?</DialogTitle>
        <DialogContent>
          <Typography sx={{ mb: 2 }}>
            Start recording in {selectedDiscordGuild?.name}/{selectedDiscordChannel?.name}?
          </Typography>
          <Stack direction="row" spacing={1} justifyContent="flex-end">
            <Button onClick={() => setDiscordConfirmOpen(false)}>Cancel</Button>
            <Button variant="contained" disabled={recordingBusy} onClick={() => void handleStartDiscord()}>
              Yes
            </Button>
          </Stack>
        </DialogContent>
      </Dialog>

      <Dialog open={!!recordPromptAdventureId} onClose={() => setRecordPromptAdventureId(null)} maxWidth="xs" fullWidth>
        <DialogTitle>Record this adventure?</DialogTitle>
        <DialogContent>
          <Typography sx={{ mb: 2 }}>
            Would you like to record audio for this adventure? You can start, pause, and complete the recording at any
            time from the adventure panel.
          </Typography>
          <Stack direction="row" spacing={1.5} justifyContent="flex-end">
            <Button onClick={() => setRecordPromptAdventureId(null)}>No</Button>
            <Button
              variant="contained"
              onClick={() => {
                const adventureId = recordPromptAdventureId;
                setRecordPromptAdventureId(null);
                if (adventureId) openRecordDialog(adventureId);
              }}
            >
              Yes
            </Button>
          </Stack>
        </DialogContent>
      </Dialog>
    </Box>
  );
}
