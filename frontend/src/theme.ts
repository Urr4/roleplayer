import { createTheme } from '@mui/material/styles';

const theme = createTheme({
  palette: {
    mode: 'dark',
    primary: {
      main: '#7c9cff',
      light: '#a9beff',
      dark: '#5879dd',
      contrastText: '#0f172a',
    },
    secondary: {
      main: '#5ec7b7',
      light: '#8ce0d4',
      dark: '#3c9b8d',
      contrastText: '#081a1f',
    },
    background: {
      default: '#111827',
      paper: '#1f2937',
    },
    text: {
      primary: '#f3f4f6',
      secondary: '#cbd5e1',
      disabled: '#64748b',
    },
    divider: 'rgba(148, 163, 184, 0.2)',
    action: {
      active: '#cbd5e1',
      hover: 'rgba(124, 156, 255, 0.08)',
      selected: 'rgba(124, 156, 255, 0.16)',
      disabled: 'rgba(148, 163, 184, 0.38)',
      disabledBackground: 'rgba(148, 163, 184, 0.12)',
    },
  },
  shape: {
    borderRadius: 12,
  },
  typography: {
    h4: { fontWeight: 700 },
    h5: { fontWeight: 600 },
    h6: { fontWeight: 600 },
    subtitle1: { fontWeight: 500 },
    button: { textTransform: 'none', fontWeight: 600 },
  },
  components: {
    MuiCssBaseline: {
      styleOverrides: {
        body: {
          backgroundColor: '#111827',
          backgroundImage: 'radial-gradient(circle at top, rgba(124, 156, 255, 0.08), transparent 45%)',
        },
      },
    },
    MuiPaper: {
      styleOverrides: {
        root: {
          backgroundImage: 'none',
          border: '1px solid rgba(148, 163, 184, 0.12)',
        },
      },
    },
    MuiCard: {
      styleOverrides: {
        root: {
          backgroundImage: 'none',
          boxShadow: '0 12px 30px rgba(15, 23, 42, 0.28)',
          border: '1px solid rgba(148, 163, 184, 0.12)',
        },
      },
    },
    MuiButton: {
      styleOverrides: {
        root: {
          borderRadius: 10,
          boxShadow: 'none',
        },
        contained: {
          boxShadow: 'none',
          '&:hover': {
            boxShadow: 'none',
          },
        },
      },
    },
    MuiTabs: {
      styleOverrides: {
        root: {
          minHeight: 52,
          backgroundColor: '#1f2937',
          border: '1px solid rgba(148, 163, 184, 0.12)',
          borderRadius: 12,
          padding: 4,
        },
        indicator: {
          height: '100%',
          borderRadius: 10,
          backgroundColor: 'rgba(124, 156, 255, 0.18)',
          zIndex: 0,
        },
      },
    },
    MuiTab: {
      styleOverrides: {
        root: {
          minHeight: 44,
          borderRadius: 10,
          color: '#94a3b8',
          fontWeight: 600,
          zIndex: 1,
          transition: 'color 0.2s ease, background-color 0.2s ease',
          '&.Mui-selected': {
            color: '#f8fafc',
          },
          '&.Mui-disabled': {
            color: 'rgba(148, 163, 184, 0.5)',
          },
        },
      },
    },
    MuiOutlinedInput: {
      styleOverrides: {
        root: {
          backgroundColor: 'rgba(15, 23, 42, 0.35)',
          '& fieldset': {
            borderColor: 'rgba(148, 163, 184, 0.24)',
          },
          '&:hover fieldset': {
            borderColor: 'rgba(148, 163, 184, 0.4)',
          },
          '&.Mui-focused fieldset': {
            borderColor: '#7c9cff',
          },
        },
      },
    },
    MuiChip: {
      styleOverrides: {
        root: {
          borderRadius: 999,
        },
      },
    },
  },
});

export default theme;
