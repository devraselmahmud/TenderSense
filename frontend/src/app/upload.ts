import { HttpClient, HttpEventType, HttpHeaders } from '@angular/common/http';
import { Component, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

interface UploadResult {
  uploadId: string;
  importedCount: number;
  qualifyingCount: number;
  warnings: string[];
}

@Component({
  selector: 'app-upload',
  imports: [RouterLink],
  templateUrl: './upload.html',
  styleUrl: './upload.scss'
})
export class Upload {
  selected = signal<File | null>(null);
  progress = signal(0);
  uploading = signal(false);
  error = signal('');
  result = signal<UploadResult | null>(null);

  constructor(private http: HttpClient) {}

  choose(event: Event) {
    const file = (event.target as HTMLInputElement).files?.[0] ?? null;
    this.error.set('');
    this.result.set(null);
    this.progress.set(0);
    if (!file) { this.selected.set(null); return; }
    const extension = file.name.split('.').pop()?.toLowerCase();
    if (!['docx', 'xlsx', 'pdf'].includes(extension ?? '')) {
      this.selected.set(null);
      this.error.set('Upload a DOCX, XLSX, or PDF file');
      return;
    }
    if (file.size > 10 * 1024 * 1024) {
      this.selected.set(null);
      this.error.set('File exceeds 10 MiB limit');
      return;
    }
    this.selected.set(file);
  }

  upload() {
    const file = this.selected();
    if (!file || this.uploading()) return;
    const body = new FormData();
    body.append('file', file);
    this.uploading.set(true);
    this.error.set('');
    this.result.set(null);
    this.http.post<UploadResult>('/api/tenders/uploads', body, {
      headers: new HttpHeaders({ Authorization: `Bearer ${localStorage.getItem('token') ?? ''}` }),
      observe: 'events',
      reportProgress: true
    }).subscribe({
      next: event => {
        if (event.type === HttpEventType.UploadProgress) this.progress.set(Math.round(100 * event.loaded / (event.total ?? event.loaded)));
        if (event.type === HttpEventType.Response) { this.result.set(event.body); this.progress.set(100); this.uploading.set(false); }
      },
      error: response => {
        this.error.set(response.error?.detail ?? response.error?.message ?? 'Could not import tender file');
        this.uploading.set(false);
      }
    });
  }

  size(file: File) { return file.size < 1024 * 1024 ? `${Math.ceil(file.size / 1024)} KiB` : `${(file.size / 1024 / 1024).toFixed(1)} MiB`; }
}
