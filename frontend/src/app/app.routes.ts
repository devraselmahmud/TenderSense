import { Routes } from '@angular/router';
import { Dashboard } from './dashboard';
import { Profile } from './profile';
import { Upload } from './upload';

export const routes: Routes = [
  { path: '', component: Dashboard },
  { path: 'profile', component: Profile },
  { path: 'upload', component: Upload },
  { path: '**', redirectTo: '' }
];
