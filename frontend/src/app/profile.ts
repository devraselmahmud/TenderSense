import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

interface ServiceLine { name: string; description: string; }
interface PastProject { title: string; client: string; sector: string; year: number | null; description: string; }
interface Certification { name: string; issuingBody: string; validUntil: string | null; }
interface ProfileData {
  configured?: boolean;
  turnoverAmount: number;
  currency: string;
  services: ServiceLine[];
  pastProjects: PastProject[];
  certifications: Certification[];
  geographies: string[];
}

@Component({
  selector: 'app-profile',
  imports: [FormsModule],
  templateUrl: './profile.html',
  styleUrl: './profile.scss'
})
export class Profile implements OnInit {
  message = signal('');
  loading = signal(false);
  saving = signal(false);
  profile: ProfileData = this.emptyProfile();

  constructor(private http: HttpClient) {}

  ngOnInit() { this.loadProfile(); }

  loadProfile() {
    this.loading.set(true);
    this.message.set('');
    this.http.get<ProfileData>('/api/profile', { headers: this.headers() }).subscribe({
      next: value => {
        if (value.configured) this.profile = {
          turnoverAmount: value.turnoverAmount,
          currency: value.currency,
          services: value.services.length ? value.services : [{ name: '', description: '' }],
          pastProjects: value.pastProjects ?? [],
          certifications: value.certifications ?? [],
          geographies: value.geographies.length ? value.geographies : ['']
        };
        this.loading.set(false);
      },
      error: () => { this.message.set('Could not load profile'); this.loading.set(false); }
    });
  }

  saveProfile() {
    if (this.saving()) return;
    this.saving.set(true);
    this.message.set('');
    this.http.put('/api/profile', this.profile, { headers: this.headers() }).subscribe({
      next: () => { this.message.set('Profile saved'); this.saving.set(false); },
      error: () => { this.message.set('Could not save profile'); this.saving.set(false); }
    });
  }

  addService() { this.profile.services.push({ name: '', description: '' }); }
  addPastProject() { this.profile.pastProjects.push({ title: '', client: '', sector: '', year: null, description: '' }); }
  removePastProject(index: number) { this.profile.pastProjects.splice(index, 1); }
  addCertification() { this.profile.certifications.push({ name: '', issuingBody: '', validUntil: null }); }
  removeCertification(index: number) { this.profile.certifications.splice(index, 1); }
  addGeography() { this.profile.geographies.push(''); }

  private emptyProfile(): ProfileData {
    return { turnoverAmount: 0, currency: 'BDT', services: [{ name: '', description: '' }], pastProjects: [], certifications: [], geographies: [''] };
  }

  private headers() { return new HttpHeaders({ Authorization: `Bearer ${localStorage.getItem('token') ?? ''}` }); }
}
