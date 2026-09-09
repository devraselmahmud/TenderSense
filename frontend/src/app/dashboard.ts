import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

interface Tender {
  id: number;
  source: string;
  title: string;
  procuringEntity?: string;
  sourceUrl?: string;
  publishDate?: string;
  deadlineDate?: string;
  grade?: string;
  eligibilityStatus?: string;
  summary?: string;
}

@Component({
  selector: 'app-dashboard',
  imports: [FormsModule],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss'
})
export class Dashboard implements OnInit {
  tenders = signal<Tender[]>([]);
  selected = signal<Tender | null>(null);
  error = signal('');
  loading = signal(false);
  decisionNote = '';
  decision = 'HOLD';

  constructor(private http: HttpClient) {}

  ngOnInit() { this.loadTenders(); }

  loadTenders() {
    if (this.loading()) return;
    this.loading.set(true);
    this.error.set('');
    this.http.get<Tender[]>('/api/tenders?publishedToday=true', { headers: this.headers() }).subscribe({
      next: value => {
        this.tenders.set(value);
        if (!value.some(tender => tender.id === this.selected()?.id)) this.selected.set(null);
        this.loading.set(false);
      },
      error: () => { this.error.set('Could not load today’s tenders'); this.loading.set(false); }
    });
  }

  open(tender: Tender) {
    this.http.get<Tender>('/api/tenders/' + tender.id, { headers: this.headers() }).subscribe({
      next: value => this.selected.set(value),
      error: () => this.error.set('Could not load tender')
    });
  }

  saveDecision() {
    const tender = this.selected();
    if (!tender) return;
    this.http.post(`/api/tenders/${tender.id}/decision`, { decision: this.decision, note: this.decisionNote }, { headers: this.headers() }).subscribe({
      next: () => { this.decisionNote = ''; this.loadTenders(); this.open(tender); },
      error: () => this.error.set('Could not save decision')
    });
  }

  private headers() { return new HttpHeaders({ Authorization: `Bearer ${localStorage.getItem('token') ?? ''}` }); }
}
