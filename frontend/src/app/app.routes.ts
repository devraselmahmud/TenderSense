import { Routes } from '@angular/router';
import { Dashboard } from './dashboard';
import { Profile } from './profile';

export const routes: Routes = [
  { path: '', component: Dashboard },
  { path: 'profile', component: Profile },
  { path: '**', redirectTo: '' }
];
