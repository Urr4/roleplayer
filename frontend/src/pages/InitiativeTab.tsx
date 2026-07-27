import { useEffect, useState } from 'react';
import {
  DndContext,
  type DragEndEvent,
  KeyboardSensor,
  PointerSensor,
  closestCenter,
  useSensor,
  useSensors,
} from '@dnd-kit/core';
import {
  SortableContext,
  arrayMove,
  sortableKeyboardCoordinates,
  useSortable,
  verticalListSortingStrategy,
} from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import { Box, Checkbox, Paper, Stack, Typography } from '@mui/material';
import DragIndicatorIcon from '@mui/icons-material/DragIndicator';
import Cookies from 'js-cookie';
import type { CharacterDto, SessionDto } from '../types';
import { getSessionCharacters } from '../api/client';

interface Props {
  session: SessionDto;
}

interface TrackerRow {
  characterId: string;
  active: boolean;
}

const cookieKey = (sessionId: string) => `roleplayer.initiative.${sessionId}`;

function loadOrder(sessionId: string): TrackerRow[] {
  const raw = Cookies.get(cookieKey(sessionId));
  if (!raw) return [];
  try {
    return JSON.parse(raw) as TrackerRow[];
  } catch {
    return [];
  }
}

function saveOrder(sessionId: string, rows: TrackerRow[]) {
  Cookies.set(cookieKey(sessionId), JSON.stringify(rows), { expires: 30 });
}

function SortableRow({ character, active, onToggle }: { character: CharacterDto; active: boolean; onToggle: () => void }) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({ id: character.id });

  return (
    <Paper
      ref={setNodeRef}
      style={{ transform: CSS.Transform.toString(transform), transition }}
      elevation={isDragging ? 6 : 2}
      sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 1.5,
        p: 1.25,
        mb: 1,
        opacity: active ? 1 : 0.4,
        filter: active ? 'none' : 'grayscale(1)',
        bgcolor: active ? '#f3e6c4' : '#d8cba8',
        border: '2px solid #5b3a24',
        borderRadius: 1,
      }}
    >
      <Box {...attributes} {...listeners} sx={{ cursor: 'grab', display: 'flex', color: '#5b3a24' }}>
        <DragIndicatorIcon />
      </Box>
      <Checkbox checked={active} onChange={onToggle} />
      <Typography variant="h6" sx={{ flexGrow: 1 }}>
        {character.name}
      </Typography>
    </Paper>
  );
}

export default function InitiativeTab({ session }: Props) {
  const [characters, setCharacters] = useState<CharacterDto[]>([]);
  const [rows, setRows] = useState<TrackerRow[]>([]);

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 5 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );

  useEffect(() => {
    getSessionCharacters(session.id).then(chars => {
      setCharacters(chars);
      const stored = loadOrder(session.id);
      const storedIds = new Set(stored.map(r => r.characterId));
      // Keep the stored order/checked-state for characters still in the session,
      // and append any newly-linked characters at the bottom (checked by default).
      const merged: TrackerRow[] = [
        ...stored.filter(r => chars.some(c => c.id === r.characterId)),
        ...chars.filter(c => !storedIds.has(c.id)).map(c => ({ characterId: c.id, active: true })),
      ];
      setRows(merged);
      saveOrder(session.id, merged);
    });
  }, [session.id]);

  const charactersById = Object.fromEntries(characters.map(c => [c.id, c]));

  const handleDragEnd = (event: DragEndEvent) => {
    const { active, over } = event;
    if (!over || active.id === over.id) return;
    setRows(prev => {
      const oldIndex = prev.findIndex(r => r.characterId === active.id);
      const newIndex = prev.findIndex(r => r.characterId === over.id);
      const next = arrayMove(prev, oldIndex, newIndex);
      saveOrder(session.id, next);
      return next;
    });
  };

  const toggleActive = (characterId: string) => {
    setRows(prev => {
      const next = prev.map(r => (r.characterId === characterId ? { ...r, active: !r.active } : r));
      saveOrder(session.id, next);
      return next;
    });
  };

  return (
    <Box>
      <Typography variant="h5" gutterBottom>
        ⚔️ The Battle Board
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2, fontStyle: 'italic' }}>
        Drag the rope handles to reorder initiative. Untick a nameplate when its bearer is out of the fight —
        this order is only kept on this browser, never saved to the vault.
      </Typography>

      {rows.length === 0 && (
        <Typography color="text.secondary" sx={{ fontStyle: 'italic' }}>
          No characters linked to this session yet — add some in the Session tab.
        </Typography>
      )}

      <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
        <SortableContext items={rows.map(r => r.characterId)} strategy={verticalListSortingStrategy}>
          <Stack>
            {rows.map(row => {
              const character = charactersById[row.characterId];
              if (!character) return null;
              return (
                <SortableRow
                  key={row.characterId}
                  character={character}
                  active={row.active}
                  onToggle={() => toggleActive(row.characterId)}
                />
              );
            })}
          </Stack>
        </SortableContext>
      </DndContext>
    </Box>
  );
}
