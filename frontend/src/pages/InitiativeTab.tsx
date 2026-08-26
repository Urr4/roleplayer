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
import type { CharacterDto, ChronicleDto } from '../types';
import { getChronicleCharacters } from '../api/client';

interface Props {
  chronicle: ChronicleDto;
}

interface TrackerRow {
  characterId: string;
  active: boolean;
}

const cookieKey = (chronicleId: string) => `roleplayer.initiative.${chronicleId}`;

function loadOrder(chronicleId: string): TrackerRow[] {
  const raw = Cookies.get(cookieKey(chronicleId));
  if (!raw) return [];
  try {
    return JSON.parse(raw) as TrackerRow[];
  } catch {
    return [];
  }
}

function saveOrder(chronicleId: string, rows: TrackerRow[]) {
  Cookies.set(cookieKey(chronicleId), JSON.stringify(rows), { expires: 30 });
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
        opacity: active ? 1 : 0.65,
        bgcolor: active ? 'background.paper' : 'rgba(15, 23, 42, 0.6)',
        border: theme => `1px solid ${active ? theme.palette.primary.main : 'rgba(148, 163, 184, 0.16)'}`,
        borderRadius: 2,
      }}
    >
      <Box {...attributes} {...listeners} sx={{ cursor: 'grab', display: 'flex', color: 'text.secondary' }}>
        <DragIndicatorIcon />
      </Box>
      <Checkbox checked={active} onChange={onToggle} />
      <Typography variant="h6" sx={{ flexGrow: 1 }}>
        {character.name}
      </Typography>
    </Paper>
  );
}

export default function InitiativeTab({ chronicle }: Props) {
  const [characters, setCharacters] = useState<CharacterDto[]>([]);
  const [rows, setRows] = useState<TrackerRow[]>([]);

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 5 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );

  useEffect(() => {
    getChronicleCharacters(chronicle.id).then(chars => {
      setCharacters(chars);
      const stored = loadOrder(chronicle.id);
      const storedIds = new Set(stored.map(row => row.characterId));
      const merged: TrackerRow[] = [
        ...stored.filter(row => chars.some(character => character.id === row.characterId)),
        ...chars.filter(character => !storedIds.has(character.id)).map(character => ({ characterId: character.id, active: true })),
      ];
      setRows(merged);
      saveOrder(chronicle.id, merged);
    });
  }, [chronicle.id]);

  const charactersById = Object.fromEntries(characters.map(character => [character.id, character]));

  const handleDragEnd = (event: DragEndEvent) => {
    const { active, over } = event;
    if (!over || active.id === over.id) return;
    setRows(previous => {
      const oldIndex = previous.findIndex(row => row.characterId === active.id);
      const newIndex = previous.findIndex(row => row.characterId === over.id);
      const next = arrayMove(previous, oldIndex, newIndex);
      saveOrder(chronicle.id, next);
      return next;
    });
  };

  const toggleActive = (characterId: string) => {
    setRows(previous => {
      const next = previous.map(row => (row.characterId === characterId ? { ...row, active: !row.active } : row));
      saveOrder(chronicle.id, next);
      return next;
    });
  };

  return (
    <Box>
      <Typography variant="h5" gutterBottom>
        ⚔️ The Battle Board
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2, fontStyle: 'italic' }}>
        Drag to reorder initiative. Untick a nameplate when its bearer is out of the fight — this order is only kept on this browser.
      </Typography>

      {rows.length === 0 && (
        <Typography color="text.secondary" sx={{ fontStyle: 'italic' }}>
          No characters linked to this chronicle yet — add some in the Chronicles tab.
        </Typography>
      )}

      <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
        <SortableContext items={rows.map(row => row.characterId)} strategy={verticalListSortingStrategy}>
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
