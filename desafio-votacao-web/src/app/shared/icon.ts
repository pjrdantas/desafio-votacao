import { Component, input } from '@angular/core';
const PATHS = {
  clock: 'M12 8v4l3 2M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z',
  plus: 'M12 5v14M5 12h14',
  close: 'm6 6 12 12M6 18 18 6',
  back: 'm15 18-6-6 6-6',
  next: 'm9 6 6 6-6 6',
  check: 'm6 12 4 4 8-8',
  ballot:
    'm9 12 2 2 4-4M12 3l3 2 3 .5.5 3L21 12l-2.5 3.5-.5 3-3 .5-3 2-3-2-3-.5-.5-3L3 12l2.5-3.5.5-3 3-.5 3-2Z',
  refresh: 'M20 7v5h-5M4 17v-5h5M6.1 6.1A8 8 0 0 1 19 8l1 4M4 12l1 4a8 8 0 0 0 12.9 1.9',
  list: 'M9 5H6a2 2 0 0 0-2 2v13h16V7a2 2 0 0 0-2-2h-3M9 3h6v4H9V3ZM8 12h8M8 16h5',
  play: 'M9 7v10l8-5-8-5Z',
  lock: 'M5 10h14v11H5V10Zm3 0V7a4 4 0 0 1 8 0v3m-4 4v3',
};
@Component({
  selector: 'app-icon',
  template:
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path [attr.d]="paths[name()]" /></svg>',
  styles:
    ':host { display:inline-flex; width:20px; height:20px; flex-shrink:0; vertical-align:middle; } svg { width:100%;height:100%; }',
})
export class Icon {
  readonly name = input<keyof typeof PATHS>('ballot');
  protected readonly paths = PATHS;
}
