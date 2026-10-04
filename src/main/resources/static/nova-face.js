// Cara LED de NOVA (canónica de portfolio-astro, adaptada a consola estática).
// Conmuta data-expression + aria-label y hace parpadear el panel.
// Sin librerías. Uso: window.NovaFace.setState('idle'|'thinking'|'speaking'|'happy'|'confused').
(function () {
  'use strict';

  // Etiquetas accesibles por estado (se anuncian con lector de pantalla).
  var ETIQUETAS = {
    idle: 'NOVA en reposo',
    thinking: 'NOVA está pensando',
    speaking: 'NOVA está respondiendo',
    happy: 'NOVA responde contenta, con fuentes',
    confused: 'NOVA no encontró evidencia'
  };

  // Cara única de la consola (un solo id basta aquí, sin dock doble).
  var cara = document.getElementById('nova-face');

  // Cambia la expresión. Devuelve true si el estado es válido, false si no.
  function setState(estado) {
    if (!Object.prototype.hasOwnProperty.call(ETIQUETAS, estado)) {
      return false;
    }
    if (cara) {
      cara.setAttribute('data-expression', estado);
      cara.setAttribute('aria-label', ETIQUETAS[estado]);
    }
    return true;
  }

  // Parpadeo LED: los ojos se apagan 120 ms (como un panel real que refresca).
  // Sin parpadeo en happy (los arcos no tienen "párpado" que cerrar).
  // Respeta prefers-reduced-motion: sin animaciones si el usuario lo pide.
  if (cara && !window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    var parpadear = function () {
      if (cara.getAttribute('data-expression') !== 'happy') {
        cara.classList.add('is-blinking');
        setTimeout(function () { cara.classList.remove('is-blinking'); }, 120);
      }
      setTimeout(parpadear, 2500 + Math.random() * 3000);
    };
    setTimeout(parpadear, 2000);
  }

  // Retina que sigue al ratón: publica el vector cara→puntero normalizado
  // a -1..1 como variables CSS --px/--py en #nova-face. El CSS traslada
  // cada pupila con su recorrido máximo y el clip del ojo la recorta.
  // Sin ratón (táctil) quedan centradas (0,0). Sin seguimiento con
  // prefers-reduced-motion. Limitado con rAF para no saturar el hilo.
  if (cara && !window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    // Recorta un número al rango -1..1.
    var sujetar = function (n) {
      return Math.max(-1, Math.min(1, n));
    };
    var pendiente = false;
    var ultimoX = 0;
    var ultimoY = 0;
    // Aplica la mirada pendiente en el siguiente fotograma.
    var aplicarMirada = function () {
      pendiente = false;
      var caja = cara.getBoundingClientRect();
      var centroX = caja.left + caja.width / 2;
      var centroY = caja.top + caja.height / 2;
      // Normaliza por media ventana: el borde de la pantalla es ±1.
      var nx = sujetar((ultimoX - centroX) / ((window.innerWidth / 2) || 1));
      var ny = sujetar((ultimoY - centroY) / ((window.innerHeight / 2) || 1));
      cara.style.setProperty('--px', nx.toFixed(3));
      cara.style.setProperty('--py', ny.toFixed(3));
    };
    document.addEventListener('mousemove', function (evento) {
      ultimoX = evento.clientX;
      ultimoY = evento.clientY;
      if (!pendiente) {
        pendiente = true;
        window.requestAnimationFrame(aplicarMirada);
      }
    });
  }

  // Expone la API pública para index.html.
  window.NovaFace = {
    setState: setState
  };
})();
