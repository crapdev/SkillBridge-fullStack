import { Directive, ElementRef, Input, OnDestroy, OnInit, inject } from '@angular/core';

/**
 * Hace aparecer el elemento (fade + desplazamiento) cuando entra en pantalla.
 * Los estilos .reveal / .is-visible están en styles.css para poder usarse desde cualquier componente.
 */
@Directive({
  selector: '[appReveal]',
  standalone: true,
  host: { class: 'reveal' }
})
export class RevealDirective implements OnInit, OnDestroy {
  /** Retraso en ms, útil para escalonar elementos de una misma lista */
  @Input() revealDelay = 0;

  private readonly el = inject(ElementRef<HTMLElement>);
  private observer?: IntersectionObserver;

  ngOnInit(): void {
    const node: HTMLElement = this.el.nativeElement;
    node.style.transitionDelay = `${this.revealDelay}ms`;

    if (typeof IntersectionObserver === 'undefined') {
      node.classList.add('is-visible');
      return;
    }
    this.observer = new IntersectionObserver(entries => {
      if (entries.some(e => e.isIntersecting)) {
        node.classList.add('is-visible');
        this.observer?.disconnect();
        // Se quita el retraso al terminar para que no afecte transiciones posteriores (p. ej. hover)
        setTimeout(() => node.style.transitionDelay = '', this.revealDelay + 700);
      }
    }, { threshold: 0.15, rootMargin: '0px 0px -40px 0px' });
    this.observer.observe(node);
  }

  ngOnDestroy(): void { this.observer?.disconnect(); }
}
