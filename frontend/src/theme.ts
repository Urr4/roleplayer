import { createTheme } from '@mui/material/styles';

// ── "Tavern Notice Board" theme ────────────────────────────────────────────────
// Warm parchment/wood palette with wax-seal accents, carved-wood shapes and a
// fantasy display font for headings — meant to feel like the GM's corkboard in
// the corner of the tavern, not a corporate dashboard.
const theme = createTheme({
  palette: {
    mode: 'light',
    primary: {
      main: '#7a2e1d', // wax-seal red
      light: '#a3452d',
      dark: '#551f13',
      contrastText: '#f6ecd9',
    },
    secondary: {
      main: '#b98b3e', // tarnished gold
      light: '#d4ac66',
      dark: '#8a672a',
      contrastText: '#2b1a10',
    },
    background: {
      default: '#e8d9b5', // parchment
      paper: '#f3e6c4',
    },
    text: {
      primary: '#3a2416', // dark ink-brown
      secondary: '#6b4c30',
    },
    divider: '#a9834f',
  },
  typography: {
    fontFamily: '"IM Fell English", "Georgia", serif',
    h1: { fontFamily: '"Cinzel", serif', fontWeight: 700, letterSpacing: 1 },
    h2: { fontFamily: '"Cinzel", serif', fontWeight: 700, letterSpacing: 1 },
    h3: { fontFamily: '"Cinzel", serif', fontWeight: 600, letterSpacing: 0.5 },
    h4: { fontFamily: '"Cinzel", serif', fontWeight: 600, letterSpacing: 0.5 },
    h5: { fontFamily: '"Cinzel", serif', fontWeight: 600 },
    h6: { fontFamily: '"Cinzel", serif', fontWeight: 600 },
    button: { fontFamily: '"Cinzel", serif', letterSpacing: 0.5 },
  },
  shape: {
    borderRadius: 4,
  },
  components: {
    MuiButton: {
      styleOverrides: {
        root: {
          borderRadius: 4,
          textTransform: 'none',
          fontWeight: 600,
          border: '1px solid rgba(58,36,22,0.35)',
          boxShadow: '0 2px 0 rgba(58,36,22,0.25)',
        },
      },
    },
    MuiPaper: {
      styleOverrides: {
        root: {
          backgroundImage:
            'repeating-linear-gradient(0deg, rgba(120,88,45,0.05) 0px, rgba(120,88,45,0.05) 1px, transparent 1px, transparent 3px)',
        },
      },
    },
    MuiCard: {
      styleOverrides: {
        root: {
          border: '1px solid rgba(58,36,22,0.35)',
          boxShadow: '0 3px 8px rgba(37,20,8,0.35)',
          backgroundColor: '#f3e6c4',
        },
      },
    },
    MuiTabs: {
      styleOverrides: {
        root: {
          minHeight: 52,
          backgroundColor: '#5b3a24',
          borderRadius: 4,
          padding: '4px 4px 0 4px',
        },
        indicator: {
          height: 4,
          backgroundColor: '#b98b3e',
        },
      },
    },
    MuiTab: {
      styleOverrides: {
        root: {
          fontFamily: '"Cinzel", serif',
          color: '#e8d9b5',
          fontWeight: 600,
          letterSpacing: 0.5,
          borderRadius: '4px 4px 0 0',
          border: '1px solid #3d2515',
          borderBottom: 'none',
          marginRight: 4,
          backgroundColor: '#4a2e1c',
          '&.Mui-selected': {
            backgroundColor: '#7a2e1d',
            color: '#f6ecd9',
          },
        },
      },
    },
    MuiChip: {
      styleOverrides: {
        root: { borderRadius: 4, fontWeight: 600 },
      },
    },
    MuiTextField: {
      styleOverrides: {
        root: {
          '& .MuiOutlinedInput-root': {
            borderRadius: 4,
            backgroundColor: 'rgba(255,255,255,0.35)',
          },
        },
      },
    },
  },
});

export default theme;
