import { useEffect, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  List,
  ListItem,
  ListItemText,
  Stack,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import DeleteIcon from '@mui/icons-material/Delete';
import TornCard from '../components/TornCard';
import { createPlayer, deletePlayer, getPlayers } from '../api/client';
import type { PlayerDto } from '../types';

export default function PlayerTab() {
  const [players, setPlayers] = useState<PlayerDto[]>([]);
  const [newPlayerName, setNewPlayerName] = useState('');
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [confirmDeletePlayer, setConfirmDeletePlayer] = useState<PlayerDto | null>(null);

  const refreshPlayers = async () => {
    setLoading(true);
    try {
      const nextPlayers = await getPlayers();
      setPlayers(nextPlayers);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to load players.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    let cancelled = false;

    void getPlayers()
      .then(nextPlayers => {
        if (cancelled) return;
        setPlayers(nextPlayers);
        setError(null);
      })
      .catch(err => {
        if (cancelled) return;
        setError(err instanceof Error ? err.message : 'Unable to load players.');
      })
      .finally(() => {
        if (cancelled) return;
        setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, []);

  const handleCreatePlayer = async () => {
    const name = newPlayerName.trim();
    if (!name || submitting) return;

    setSubmitting(true);
    try {
      await createPlayer(name);
      setNewPlayerName('');
      await refreshPlayers();
      // TODO: Add player rename support once the backend exposes an update endpoint.
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to create player.');
    } finally {
      setSubmitting(false);
    }
  };

  const handleDeletePlayer = async () => {
    if (!confirmDeletePlayer) return;
    try {
      await deletePlayer(confirmDeletePlayer.id);
      await refreshPlayers();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to delete player.');
    } finally {
      setConfirmDeletePlayer(null);
    }
  };

  return (
    <Box>
      <Typography variant="h5" gutterBottom>
        👥 Players
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2, fontStyle: 'italic' }}>
        Manage the global pool of players that can later be assigned to characters.
      </Typography>

      {error && (
        <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError(null)}>
          {error}
        </Alert>
      )}

      <Stack spacing={3} direction={{ xs: 'column', md: 'row' }} alignItems="stretch">
        <TornCard sx={{ flex: 1, minWidth: 0 }}>
          <Stack spacing={2}>
            <Typography variant="h6">Add Player</Typography>
            <TextField
              label="Player name"
              size="small"
              value={newPlayerName}
              onChange={e => setNewPlayerName(e.target.value)}
              onKeyDown={e => e.key === 'Enter' && void handleCreatePlayer()}
              disabled={submitting}
              fullWidth
            />
            <Button
              variant="contained"
              startIcon={<AddIcon />}
              onClick={() => void handleCreatePlayer()}
              disabled={submitting || !newPlayerName.trim()}
              sx={{ alignSelf: 'flex-start' }}
            >
              {submitting ? 'Adding…' : 'Add Player'}
            </Button>
          </Stack>
        </TornCard>

        <TornCard sx={{ flex: 1, minWidth: 0 }}>
          <Stack spacing={2}>
            <Typography variant="h6">All Players</Typography>
            {loading ? (
              <Stack direction="row" spacing={1} alignItems="center">
                <CircularProgress size={20} />
                <Typography color="text.secondary">Loading players…</Typography>
              </Stack>
            ) : players.length === 0 ? (
              <Typography color="text.secondary" sx={{ fontStyle: 'italic' }}>
                No players yet — add your first player using the form.
              </Typography>
            ) : (
              <List disablePadding>
                {players.map(player => (
                  <ListItem
                    key={player.id}
                    disableGutters
                    divider
                    secondaryAction={
                      <Tooltip title="Delete player">
                        <IconButton size="small" color="error" onClick={() => setConfirmDeletePlayer(player)}>
                          <DeleteIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                    }
                  >
                    <ListItemText primary={player.name} />
                  </ListItem>
                ))}
              </List>
            )}
          </Stack>
        </TornCard>
      </Stack>

      <Dialog open={!!confirmDeletePlayer} onClose={() => setConfirmDeletePlayer(null)} maxWidth="xs" fullWidth>
        <DialogTitle>Delete player?</DialogTitle>
        <DialogContent>
          <Typography>
            Are you sure you want to delete "{confirmDeletePlayer?.name}"? This cannot be undone.
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setConfirmDeletePlayer(null)}>Cancel</Button>
          <Button color="error" variant="contained" onClick={() => void handleDeletePlayer()}>
            Delete
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
