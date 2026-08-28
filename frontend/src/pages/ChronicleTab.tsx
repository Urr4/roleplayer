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
  Dialog,
  DialogContent,
  DialogTitle,
  Divider,
  FormControlLabel,
  Grid,
  IconButton,
  List,
  ListItem,
  ListItemButton,
  ListItemText,
  Stack,
  Switch,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
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
import type { AdventureCharacterDto, AdventureDto, CharacterDto, ChronicleDto, DiscordGuildDto, DiscordVoiceChannelDto, PlayerDto, RecordingDto } from '../types';
import {
  addAdventureCharacter,
  appendRecordingChunk,
  createAdventure,
  createCharacter,
  createChronicle,
  getAdventureCharacters,
  getAdventures,
  getAllCharacters,
  getCharacterSheetUrl,
  getChronicleCharacters,
  getDiscordGuilds,
  getDiscordVoiceChannels,
  getPlayers,
  getRecordings,
  importCharacterIntoChronicle,
  pauseRecording,
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

export default function ChronicleTab({
  chronicles,
  activeChronicleId,
  onSelectChronicle,
  onChroniclesChanged,
  onActiveAdventureChanged,
}: Props) {
  const [newChronicleName, setNewChronicleName] = useState('');

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

  const [recordDialogOpen, setRecordDialogOpen] = useState(false);
  const [recordDialogAdventureId, setRecordDialogAdventureId] = useState<string | null>(null);
  const [recordPromptAdventureId, setRecordPromptAdventureId] = useState<string | null>(null);
  const [discordGuilds, setDiscordGuilds] = useState<DiscordGuildDto[]>([]);
  const [discordGuildsLoading, setDiscordGuildsLoading] = useState(false);
  const [selectedDiscordGuild, setSelectedDiscordGuild] = useState<DiscordGuildDto | null>(null);
  const [discordChannels, setDiscordChannels] = useState<DiscordVoiceChannelDto[]>([]);
  const [discordChannelsLoading, setDiscordChannelsLoading] = useState(false);
  const [selectedDiscordChannel, setSelectedDiscordChannel] = useState<DiscordVoiceChannelDto | null>(null);
  const [writeTranscriptToChat, setWriteTranscriptToChat] = useState(false);
  const [discordConfirmOpen, setDiscordConfirmOpen] = useState(false);
  const [liveRecording, setLiveRecording] = useState<RecordingDto | null>(null);
  const [liveRecordingAdventureId, setLiveRecordingAdventureId] = useState<string | null>(null);
  const [recordingBusy, setRecordingBusy] = useState(false);
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
        return { liveRecordingAdventureId: null, liveRecording: null as RecordingDto | null };
      }

      try {
        const recordings = await getRecordings(adventureId);
        const inProgress = recordings.find(recording => recording.status === 'RECORDING' || recording.status === 'PAUSED');
        return {
          liveRecordingAdventureId: inProgress ? adventureId : null,
          liveRecording: inProgress ?? null,
        };
      } catch {
        return { liveRecordingAdventureId: null, liveRecording: null as RecordingDto | null };
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
  }, [refreshRoster]);

  useEffect(() => {
    let cancelled = false;
    void fetchRecordingState(expandedAdventureId).then(({ liveRecordingAdventureId: currentLiveRecordingAdventureId, liveRecording: currentLiveRecording }) => {
      if (cancelled) return;
      setLiveRecordingAdventureId(currentLiveRecordingAdventureId);
      setLiveRecording(currentLiveRecording);
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
      void fetchRecordingState(expandedAdventureId).then(({ liveRecordingAdventureId: currentLiveRecordingAdventureId, liveRecording: currentLiveRecording }) => {
        setLiveRecordingAdventureId(currentLiveRecordingAdventureId);
        setLiveRecording(currentLiveRecording);
      });
    }, 5000);
    return () => window.clearInterval(interval);
  }, [expandedAdventureId, fetchRecordingState]);


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
    if (!name) return;
    try {
      const chronicle = await createChronicle(name);
      setNewChronicleName('');
      onSelectChronicle(chronicle.id);
      onChroniclesChanged();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to create chronicle.');
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
      const recording = await startRecording(adventureId, 'DISCORD', selectedDiscordChannel.id, writeTranscriptToChat);
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
        mediaRecorderRef.current?.pause();
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
    setWriteTranscriptToChat(false);
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
                <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
                  <TextField
                    label="New chronicle name"
                    size="small"
                    fullWidth
                    value={newChronicleName}
                    onChange={event => setNewChronicleName(event.target.value)}
                    onKeyDown={event => event.key === 'Enter' && void handleCreateChronicle()}
                  />
                  <Button variant="contained" startIcon={<AddIcon />} onClick={() => void handleCreateChronicle()}>
                    Create
                  </Button>
                </Stack>

                <List sx={{ py: 0 }}>
                  {chronicles.map(chronicle => (
                    <ListItemButton
                      key={chronicle.id}
                      selected={chronicle.id === activeChronicleId}
                      onClick={() => onSelectChronicle(chronicle.id)}
                      sx={{ borderRadius: 2, mb: 0.5 }}
                    >
                      <ListItemText primary={chronicle.name} secondary={new Date(chronicle.createdAt).toLocaleString()} />
                    </ListItemButton>
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
                                adventure.status === 'ACTIVE' ? (
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
                                ) : null
                              }
                              sx={{ borderRadius: 2, mb: 0.5 }}
                            >
                              <ListItemButton selected={adventure.id === expandedAdventureId} onClick={() => handleSelectAdventure(adventure.id)}>
                                <ListItemText
                                  primary={
                                    <Stack direction="row" spacing={0.75} alignItems="center">
                                      {adventure.status === 'ACTIVE' && <ExploreIcon fontSize="small" color="success" />}
                                      <Typography component="span">{adventure.name}</Typography>
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

                            <Box>
                              <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" alignItems={{ sm: 'center' }} spacing={1.5}>
                                <Typography variant="subtitle2" color="text.secondary">
                                  Recording & Transcript
                                </Typography>
                                {liveRecordingAdventureId === expandedAdventureId && liveRecording ? (
                                  <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
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

                              <Box sx={{ mt: 2 }}>
                                <TranscriptPanel
                                  adventureId={expandedAdventureId}
                                  isLive={
                                    liveRecordingAdventureId === expandedAdventureId &&
                                    (liveRecording?.status === 'RECORDING' || liveRecording?.status === 'PAUSED')
                                  }
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

      <Dialog open={!!viewerUrl} onClose={() => setViewerUrl(null)} maxWidth="md" fullWidth>
        <DialogTitle sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          {viewerTitle}'s Character Sheet
          <IconButton onClick={() => setViewerUrl(null)}>
            <CloseIcon />
          </IconButton>
        </DialogTitle>
        <DialogContent sx={{ height: '75vh' }}>{viewerUrl && <embed src={viewerUrl} type="application/pdf" width="100%" height="100%" />}</DialogContent>
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
            <FormControlLabel
              control={
                <Switch
                  checked={writeTranscriptToChat}
                  onChange={event => setWriteTranscriptToChat(event.target.checked)}
                />
              }
              label="Write transcript to chat?"
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
