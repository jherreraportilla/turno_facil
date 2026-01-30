/**
 * TurnoFacil - Utilidades CSRF
 * Incluir este script en todas las paginas que hagan peticiones AJAX
 */

// Obtener token CSRF de la cookie
function getCsrfToken() {
    const name = 'XSRF-TOKEN=';
    const cookies = document.cookie.split(';');
    for (let cookie of cookies) {
        cookie = cookie.trim();
        if (cookie.startsWith(name)) {
            return decodeURIComponent(cookie.substring(name.length));
        }
    }
    return null;
}

// Fetch con CSRF token automatico
async function secureFetch(url, options = {}) {
    const csrfToken = getCsrfToken();
    const headers = new Headers(options.headers || {});

    // Solo añadir CSRF para metodos que modifican datos
    const method = (options.method || 'GET').toUpperCase();
    if (['POST', 'PUT', 'PATCH', 'DELETE'].includes(method) && csrfToken) {
        headers.set('X-XSRF-TOKEN', csrfToken);
    }

    return fetch(url, {
        ...options,
        headers: headers,
        credentials: 'same-origin'
    });
}

// Configurar fetch global para incluir CSRF automaticamente
(function() {
    const originalFetch = window.fetch;

    window.fetch = function(url, options = {}) {
        const csrfToken = getCsrfToken();
        const method = (options.method || 'GET').toUpperCase();

        // Solo añadir para metodos que modifican datos
        if (['POST', 'PUT', 'PATCH', 'DELETE'].includes(method) && csrfToken) {
            options.headers = options.headers || {};

            // Si es un Headers object
            if (options.headers instanceof Headers) {
                options.headers.set('X-XSRF-TOKEN', csrfToken);
            } else {
                options.headers['X-XSRF-TOKEN'] = csrfToken;
            }
        }

        // Asegurar credentials
        if (!options.credentials) {
            options.credentials = 'same-origin';
        }

        return originalFetch.call(this, url, options);
    };
})();

// Para formularios dinamicos, añadir input hidden con el token
function addCsrfToForm(form) {
    const csrfToken = getCsrfToken();
    if (!csrfToken || !form) return;

    // Verificar si ya existe
    let input = form.querySelector('input[name="_csrf"]');
    if (!input) {
        input = document.createElement('input');
        input.type = 'hidden';
        input.name = '_csrf';
        form.appendChild(input);
    }
    input.value = csrfToken;
}

// Configurar jQuery AJAX si esta disponible
if (typeof jQuery !== 'undefined') {
    jQuery.ajaxSetup({
        beforeSend: function(xhr) {
            const csrfToken = getCsrfToken();
            if (csrfToken) {
                xhr.setRequestHeader('X-XSRF-TOKEN', csrfToken);
            }
        }
    });
}

console.log('CSRF Utils loaded');
