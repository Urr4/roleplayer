import { Paper } from '@mui/material';
import type { PaperProps } from '@mui/material';

interface TornCardProps extends PaperProps {
  rotate?: number;
}

/**
 * A parchment clipping with a torn/deckled edge and a subtle rotation, used for
 * character/NPC "index card" style listings across the tavern-board theme.
 */
export default function TornCard({ rotate = 0, sx, children, ...rest }: TornCardProps) {
  return (
    <Paper
      elevation={0}
      className="torn-edge"
      sx={{
        p: 2,
        transform: rotate ? `rotate(${rotate}deg)` : 'none',
        ...sx,
      }}
      {...rest}
    >
      {children}
    </Paper>
  );
}
