import layoutContract from '../../../shared/ui-layout.json';
import actionColorContract from '../../../shared/ui-action-colors.json';

export const actionColors = Object.freeze(actionColorContract);

export const uiLayout = Object.freeze(layoutContract);

export const colors = {
  primary: '#2563EB',
  background: '#F5F5F7',
  surface: '#ffffff',
  surfaceAlt: '#F0F1F4',
  controlSurface: '#FFFFFF',
  controlBorder: '#D9DCE3',
  controlPressed: '#F0F2F6',
  controlSelectedSurface: '#EEF3FF',
  controlSelectedText: actionColors.primary.foreground,
  border: '#E7E8EC',
  ink: '#202124',
  text: '#202124',
  muted: '#656B76',
  placeholder: '#9aa4b2',
  danger: '#dc2626',
  dangerSurface: '#fef2f2',
  success: '#16a34a',
  warning: '#d97706',
  management: '#0F766E',
};
