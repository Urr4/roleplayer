import { useEffect, useRef, useState } from 'react';
import { Box, IconButton, List, ListItem, ListItemText, Stack, Tooltip, Typography } from '@mui/material';
import OpenInFullIcon from '@mui/icons-material/OpenInFull';
import type { TranscriptSegmentDto } from '../types';
import { getAdventureTranscript, subscribeToAdventureTranscript } from '../api/client';

interface Props {
  adventureId: string;
  /** Whether a recording is currently RECORDING/PAUSED for this adventure — controls the live SSE subscription. */
  isLive: boolean;
  /** 'compact' (default) shows a height-limited preview with a button to open the full view; 'full' shows the entire transcript with no height limit. */
  variant?: 'compact' | 'full';
  /** Called when the user clicks the "open full transcript" action (only relevant in 'compact' mode). */
  onOpenFull?: () => void;
}

function formatElapsed(ms: number): string {
  const totalSeconds = Math.max(0, Math.floor(ms / 1000));
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
}

export default function TranscriptPanel({ adventureId, isLive, variant = 'compact', onOpenFull }: Props) {
  const [segments, setSegments] = useState<TranscriptSegmentDto[]>([]);
  const seenIds = useRef<Set<string>>(new Set());
  const bottomRef = useRef<HTMLDivElement | null>(null);
  const previousLiveStateRef = useRef({ adventureId, isLive });


  useEffect(() => {
    seenIds.current = new Set();
    getAdventureTranscript(adventureId).then(loaded => {
      seenIds.current = new Set(loaded.map(segment => segment.id));
      setSegments(loaded);
    });
  }, [adventureId]);

  // Poll periodically even outside SSE-covered live recordings so that
  // async processing (e.g. an uploaded file's transcription completing on
  // the backend) is reflected here without requiring a manual refresh.
  useEffect(() => {
    if (isLive) return;
    const interval = window.setInterval(() => {
      getAdventureTranscript(adventureId).then(loaded => {
        setSegments(previousSegments => {
          const merged = new Map(previousSegments.map(segment => [segment.id, segment]));
          let changed = loaded.length !== previousSegments.length;
          loaded.forEach(segment => {
            if (!merged.has(segment.id)) changed = true;
            merged.set(segment.id, segment);
          });
          if (!changed) return previousSegments;
          const mergedSegments = [...merged.values()].sort((a, b) => a.startMs - b.startMs);
          seenIds.current = new Set(mergedSegments.map(segment => segment.id));
          return mergedSegments;
        });
      });
    }, 5000);
    return () => window.clearInterval(interval);
  }, [adventureId, isLive]);

  useEffect(() => {
    const previous = previousLiveStateRef.current;
    previousLiveStateRef.current = { adventureId, isLive };

    if (previous.adventureId !== adventureId || !previous.isLive || isLive) return;

    getAdventureTranscript(adventureId).then(loaded => {
      setSegments(previousSegments => {
        const merged = new Map(previousSegments.map(segment => [segment.id, segment]));
        loaded.forEach(segment => {
          merged.set(segment.id, segment);
        });

        const mergedSegments = [...merged.values()].sort((a, b) => a.startMs - b.startMs);
        seenIds.current = new Set(mergedSegments.map(segment => segment.id));
        return mergedSegments;
      });
    });
  }, [adventureId, isLive]);

  useEffect(() => {
    if (!isLive) return;
    const source = subscribeToAdventureTranscript(adventureId, segment => {
      if (seenIds.current.has(segment.id)) return;
      seenIds.current.add(segment.id);
      setSegments(previous => [...previous, segment]);
    });
    return () => source.close();
  }, [adventureId, isLive]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ block: 'nearest' });
  }, [segments.length]);

  return (
    <Box>
      <Stack direction="row" alignItems="center" justifyContent="space-between" spacing={1}>
        <Typography variant="h5" gutterBottom>
          🎙️ Adventure Transcript {isLive && <Typography component="span" color="error">● live</Typography>}
        </Typography>
        {variant === 'compact' && onOpenFull && (
          <Tooltip title="Open full transcript">
            <IconButton onClick={onOpenFull} size="small">
              <OpenInFullIcon fontSize="small" />
            </IconButton>
          </Tooltip>
        )}
      </Stack>
      <Box
        sx={{
          maxHeight: variant === 'full' ? 'none' : 320,
          overflowY: variant === 'full' ? 'visible' : 'auto',
          border: '1px solid rgba(58,36,22,0.3)',
          bgcolor: 'rgba(0,0,0,0.03)',
        }}
      >
        <List dense>
          {segments.map(segment => (
            <ListItem key={segment.id} sx={{ py: 0.25 }}>
              <ListItemText
                primary={
                  <Stack direction="row" spacing={1}>
                    <Typography variant="body2" color="text.secondary" sx={{ fontFamily: 'monospace' }}>
                      [{formatElapsed(segment.startMs)}]
                    </Typography>
                    <Typography variant="body2" fontWeight="bold">
                      {segment.speakerLabel}:
                    </Typography>
                    <Typography variant="body2">{segment.text}</Typography>
                  </Stack>
                }
              />
            </ListItem>
          ))}
          {segments.length === 0 && (
            <ListItem>
              <ListItemText
                primary={
                  <Typography color="text.secondary" sx={{ fontStyle: 'italic' }}>
                    No transcript yet — record or upload audio for this adventure to see it here.
                  </Typography>
                }
              />
            </ListItem>
          )}
          <div ref={bottomRef} />
        </List>
      </Box>
    </Box>
  );
}
