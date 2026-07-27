import { useEffect, useState } from 'react';
import {
  Autocomplete,
  Box,
  Button,
  Dialog,
  DialogContent,
  DialogTitle,
  Divider,
  Grid,
  IconButton,
  List,
  ListItemButton,
  ListItemText,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import PictureAsPdfIcon from '@mui/icons-material/PictureAsPdf';
import UploadFileIcon from '@mui/icons-material/UploadFile';
import CloseIcon from '@mui/icons-material/Close';
import TornCard from '../components/TornCard';
import type { CharacterDto, PlayerDto, SessionDto } from '../types';
import {
  createCharacter,
  createPlayer,
  createSession,
  getAllCharacters,
  getCharacterSheetUrl,
  getPlayers,
  getSessionCharacters,
  linkCharacterToSession,
  replaceCharacterSheet,
  unlinkCharacterFromSession,
} from '../api/client';

interface Props {
  sessions: SessionDto[];
  activeSessionId: string | null;
  onSelectSession: (id: string) => void;
  onSessionsChanged: () => void;
}

export default function SessionTab({ sessions, activeSessionId, onSelectSession, onSessionsChanged }: Props) {
  const [newSessionName, setNewSessionName] = useState('');

  const [allPlayers, setAllPlayers] = useState<PlayerDto[]>([]);
  const [allCharacters, setAllCharacters] = useState<CharacterDto[]>([]);
  const [sessionCharacters, setSessionCharacters] = useState<CharacterDto[]>([]);

  const [newPlayerName, setNewPlayerName] = useState('');
  const [selectedPlayer, setSelectedPlayer] = useState<PlayerDto | null>(null);
  const [newCharacterName, setNewCharacterName] = useState('');
  const [newCharacterSheet, setNewCharacterSheet] = useState<File | null>(null);
  const [importCharacter, setImportCharacter] = useState<CharacterDto | null>(null);

  const [viewerUrl, setViewerUrl] = useState<string | null>(null);
  const [viewerTitle, setViewerTitle] = useState<string>('');

  const refreshRoster = () => {
    getPlayers().then(setAllPlayers);
    getAllCharacters().then(setAllCharacters);
  };

  const refreshSessionCharacters = () => {
    if (activeSessionId) {
      getSessionCharacters(activeSessionId).then(setSessionCharacters);
    } else {
      setSessionCharacters([]);
    }
  };

  useEffect(refreshRoster, []);
  useEffect(refreshSessionCharacters, [activeSessionId]);

  const handleCreateSession = async () => {
    if (!newSessionName.trim()) return;
    const session = await createSession(newSessionName.trim());
    setNewSessionName('');
    onSessionsChanged();
    onSelectSession(session.id);
  };

  const handleCreatePlayer = async () => {
    if (!newPlayerName.trim()) return;
    const player = await createPlayer(newPlayerName.trim());
    setNewPlayerName('');
    refreshRoster();
    setSelectedPlayer(player);
  };

  const handleCreateCharacter = async () => {
    if (!newCharacterName.trim() || !selectedPlayer || !activeSessionId) return;
    const character = await createCharacter(newCharacterName.trim(), selectedPlayer.id, newCharacterSheet ?? undefined);
    setNewCharacterName('');
    setNewCharacterSheet(null);
    await linkCharacterToSession(activeSessionId, character.id);
    refreshRoster();
    refreshSessionCharacters();
  };

  const handleImportCharacter = async () => {
    if (!importCharacter || !activeSessionId) return;
    await linkCharacterToSession(activeSessionId, importCharacter.id);
    setImportCharacter(null);
    refreshSessionCharacters();
  };

  const handleRemoveCharacter = async (characterId: string) => {
    if (!activeSessionId) return;
    await unlinkCharacterFromSession(activeSessionId, characterId);
    refreshSessionCharacters();
  };

  const handleUploadSheet = async (characterId: string, file: File) => {
    await replaceCharacterSheet(characterId, file);
    refreshRoster();
    refreshSessionCharacters();
  };

  const handleViewSheet = async (character: CharacterDto) => {
    const url = await getCharacterSheetUrl(character.id);
    setViewerUrl(url);
    setViewerTitle(character.name);
  };

  const playerName = (playerId: string) => allPlayers.find(p => p.id === playerId)?.name ?? 'Unknown adventurer';

  const importableCharacters = allCharacters.filter(c => !sessionCharacters.some(sc => sc.id === c.id));

  return (
    <Box>
      {/* ── Tavern log: sessions ─────────────────────────────────────────── */}
      <Typography variant="h5" gutterBottom>
        📜 The Tavern Log
      </Typography>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ mb: 2 }}>
        <TextField
          label="New session name"
          size="small"
          value={newSessionName}
          onChange={e => setNewSessionName(e.target.value)}
          onKeyDown={e => e.key === 'Enter' && handleCreateSession()}
          fullWidth
        />
        <Button variant="contained" startIcon={<AddIcon />} onClick={handleCreateSession}>
          Start Session
        </Button>
      </Stack>

      <List sx={{ mb: 3 }}>
        {sessions.map(session => (
          <ListItemButton
            key={session.id}
            selected={session.id === activeSessionId}
            onClick={() => onSelectSession(session.id)}
            sx={{
              border: '1px solid rgba(58,36,22,0.3)',
              mb: 0.5,
              '&.Mui-selected': { bgcolor: 'rgba(122,46,29,0.15)' },
            }}
          >
            <ListItemText
              primary={session.name}
              secondary={new Date(session.createdAt).toLocaleString()}
            />
          </ListItemButton>
        ))}
        {sessions.length === 0 && (
          <Typography color="text.secondary" sx={{ fontStyle: 'italic' }}>
            No sessions yet — scribe your first entry in the log above.
          </Typography>
        )}
      </List>

      {activeSessionId && (
        <>
          <Divider sx={{ mb: 2 }} />
          <Typography variant="h5" gutterBottom>
            🧑‍🤝‍🧑 Party Roster
          </Typography>

          <Grid container spacing={2} sx={{ mb: 2 }}>
            <Grid size={{ xs: 12, sm: 4 }}>
              <Stack spacing={1}>
                <TextField
                  label="New player name"
                  size="small"
                  value={newPlayerName}
                  onChange={e => setNewPlayerName(e.target.value)}
                />
                <Button size="small" variant="outlined" onClick={handleCreatePlayer}>
                  Add Player
                </Button>
                <Autocomplete
                  options={allPlayers}
                  getOptionLabel={p => p.name}
                  value={selectedPlayer}
                  onChange={(_, v) => setSelectedPlayer(v)}
                  renderInput={params => <TextField {...params} label="Player for new character" size="small" />}
                />
              </Stack>
            </Grid>
            <Grid size={{ xs: 12, sm: 4 }}>
              <Stack spacing={1}>
                <TextField
                  label="New character name"
                  size="small"
                  value={newCharacterName}
                  onChange={e => setNewCharacterName(e.target.value)}
                />
                <Button component="label" size="small" startIcon={<UploadFileIcon />} variant="outlined">
                  {newCharacterSheet ? newCharacterSheet.name : 'Attach sheet (PDF)'}
                  <input
                    hidden
                    type="file"
                    accept="application/pdf"
                    onChange={e => setNewCharacterSheet(e.target.files?.[0] ?? null)}
                  />
                </Button>
                <Button
                  variant="contained"
                  onClick={handleCreateCharacter}
                  disabled={!selectedPlayer || !newCharacterName.trim()}
                >
                  Add Character
                </Button>
              </Stack>
            </Grid>
            <Grid size={{ xs: 12, sm: 4 }}>
              <Stack spacing={1}>
                <Autocomplete
                  options={importableCharacters}
                  getOptionLabel={c => `${c.name} (${playerName(c.playerId)})`}
                  value={importCharacter}
                  onChange={(_, v) => setImportCharacter(v)}
                  renderInput={params => <TextField {...params} label="Import existing character" size="small" />}
                />
                <Button size="small" variant="outlined" onClick={handleImportCharacter} disabled={!importCharacter}>
                  Import into Session
                </Button>
              </Stack>
            </Grid>
          </Grid>

          <Grid container spacing={2}>
            {sessionCharacters.map((character, i) => (
              <Grid key={character.id} size={{ xs: 12, sm: 6, md: 4 }}>
                <TornCard rotate={i % 2 === 0 ? -1.5 : 1.5}>
                  <Stack direction="row" justifyContent="space-between" alignItems="flex-start">
                    <Box>
                      <Typography variant="h6">{character.name}</Typography>
                      <Typography variant="body2" color="text.secondary">
                        played by {playerName(character.playerId)}
                      </Typography>
                    </Box>
                    <IconButton size="small" onClick={() => handleRemoveCharacter(character.id)}>
                      <CloseIcon fontSize="small" />
                    </IconButton>
                  </Stack>
                  <Stack direction="row" spacing={1} sx={{ mt: 1.5 }}>
                    {character.hasSheet ? (
                      <Button
                        size="small"
                        startIcon={<PictureAsPdfIcon />}
                        onClick={() => handleViewSheet(character)}
                      >
                        View Sheet
                      </Button>
                    ) : (
                      <Button size="small" component="label" startIcon={<UploadFileIcon />}>
                        Upload Sheet
                        <input
                          hidden
                          type="file"
                          accept="application/pdf"
                          onChange={e => {
                            const file = e.target.files?.[0];
                            if (file) handleUploadSheet(character.id, file);
                          }}
                        />
                      </Button>
                    )}
                  </Stack>
                </TornCard>
              </Grid>
            ))}
            {sessionCharacters.length === 0 && (
              <Grid size={12}>
                <Typography color="text.secondary" sx={{ fontStyle: 'italic' }}>
                  No adventurers signed the log for this session yet.
                </Typography>
              </Grid>
            )}
          </Grid>
        </>
      )}

      <Dialog open={!!viewerUrl} onClose={() => setViewerUrl(null)} maxWidth="md" fullWidth>
        <DialogTitle sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          {viewerTitle}'s Character Sheet
          <IconButton onClick={() => setViewerUrl(null)}>
            <CloseIcon />
          </IconButton>
        </DialogTitle>
        <DialogContent sx={{ height: '75vh' }}>
          {viewerUrl && (
            <embed src={viewerUrl} type="application/pdf" width="100%" height="100%" />
          )}
        </DialogContent>
      </Dialog>
    </Box>
  );
}
