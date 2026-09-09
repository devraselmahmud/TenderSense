import { HttpClient, HttpClientModule } from '@angular/common/http';
import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',
  imports: [FormsModule, HttpClientModule, RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App {
  loggedIn = signal(Boolean(localStorage.getItem('token')));
  error = signal('');
  email = '';
  password = '';

  constructor(private http: HttpClient, private router: Router) {}

  login() {
    this.error.set('');
    this.http.post<{ token: string }>('/api/auth/login', { email: this.email, password: this.password }).subscribe({
      next: response => { localStorage.setItem('token', response.token); this.loggedIn.set(true); },
      error: () => this.error.set('Login failed')
    });
  }

  logout() {
    localStorage.removeItem('token');
    this.loggedIn.set(false);
    this.router.navigateByUrl('/');
  }
}
