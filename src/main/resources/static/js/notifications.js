/**
 * TurnoFacil - Sistema de Notificaciones (JavaScript Vanilla + SSE)
 */

let eventSource = null;

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

// Fetch con CSRF token
async function fetchWithCsrf(url, options = {}) {
    const csrfToken = getCsrfToken();
    const headers = options.headers || {};

    if (csrfToken) {
        headers['X-XSRF-TOKEN'] = csrfToken;
    }

    return fetch(url, {
        ...options,
        headers: headers,
        credentials: 'same-origin'
    });
}

document.addEventListener('DOMContentLoaded', function() {
    // Cargar badge inicial
    updateNotificationBadge();

    // Cargar notificaciones cuando se abre el dropdown
    const dropdown = document.getElementById('notificationsDropdown');
    if (dropdown) {
        dropdown.addEventListener('show.bs.dropdown', loadNotifications);
    }

    // Marcar todas como leidas
    const markAllBtn = document.getElementById('markAllReadBtn');
    if (markAllBtn) {
        markAllBtn.addEventListener('click', function(e) {
            e.preventDefault();
            markAllNotificationsRead();
        });
    }

    // Conectar SSE para tiempo real
    connectSSE();
});

async function loadNotifications() {
    const list = document.getElementById('notificationsList');
    if (!list) return;

    list.innerHTML = `
        <div class="notification-loading">
            <div class="spinner-border spinner-border-sm text-secondary"></div>
            <span class="ms-2">Cargando...</span>
        </div>
    `;

    try {
        const response = await fetchWithCsrf('/api/notifications');
        if (!response.ok) throw new Error('Error ' + response.status);

        const notifications = await response.json();

        if (notifications.length === 0) {
            list.innerHTML = `
                <div class="notification-empty">
                    <i class="bi bi-bell-slash fs-3 mb-2 d-block"></i>
                    No hay notificaciones
                </div>
            `;
            return;
        }

        list.innerHTML = notifications.map(n => `
            <div class="notification-item ${n.read ? '' : 'unread'}"
                 onclick="handleNotificationClick(${n.id}, '${n.link || ''}')">
                <div class="notification-icon" style="background: ${n.typeColor}20; color: ${n.typeColor};">
                    <i class="bi ${n.typeIcon}"></i>
                </div>
                <div class="notification-content">
                    <div class="notification-title">${escapeHtml(n.title)}</div>
                    <div class="notification-message">${escapeHtml(n.message)}</div>
                    <div class="notification-time">${escapeHtml(n.timeAgo)}</div>
                </div>
            </div>
        `).join('');

    } catch (error) {
        console.error('Error loading notifications:', error);
        list.innerHTML = '<div class="notification-empty">Error al cargar</div>';
    }
}

async function updateNotificationBadge() {
    try {
        const response = await fetchWithCsrf('/api/notifications/unread-count');
        if (!response.ok) return;

        const data = await response.json();
        const badge = document.getElementById('notificationBadge');

        if (badge) {
            if (data.count > 0) {
                badge.textContent = data.count > 99 ? '99+' : data.count;
                badge.style.display = 'flex';
            } else {
                badge.style.display = 'none';
            }
        }
    } catch (error) {
        console.error('Error updating badge:', error);
    }
}

async function handleNotificationClick(id, link) {
    try {
        await fetchWithCsrf(`/api/notifications/${id}/read`, { method: 'PATCH' });

        if (link) {
            window.location.href = link;
        } else {
            loadNotifications();
            updateNotificationBadge();
        }
    } catch (error) {
        console.error('Error marking as read:', error);
    }
}

async function markAllNotificationsRead() {
    try {
        await fetchWithCsrf('/api/notifications/mark-all-read', { method: 'PATCH' });
        loadNotifications();
        updateNotificationBadge();
    } catch (error) {
        console.error('Error marking all as read:', error);
    }
}

function connectSSE() {
    if (eventSource) {
        eventSource.close();
    }

    eventSource = new EventSource('/api/notifications/stream');

    eventSource.addEventListener('connected', function() {
        console.log('SSE: Conectado a notificaciones en tiempo real');
    });

    eventSource.addEventListener('notification', function(e) {
        console.log('SSE: Nueva notificacion recibida');
        const notification = JSON.parse(e.data);

        // Actualizar badge
        updateNotificationBadge();

        // Mostrar toast
        showToast(notification);

        // Si el dropdown esta abierto, recargar
        const dropdown = document.getElementById('notificationsDropdown');
        if (dropdown && dropdown.querySelector('.dropdown-menu.show')) {
            loadNotifications();
        }
    });

    eventSource.onerror = function() {
        console.log('SSE: Error, reconectando en 5s...');
        eventSource.close();
        setTimeout(connectSSE, 5000);
    };
}

function showToast(notification) {
    const container = document.getElementById('toastContainer');
    if (!container) return;

    const toast = document.createElement('div');
    toast.className = 'notification-toast';
    toast.innerHTML = `
        <div class="notification-toast-icon" style="background: ${notification.typeColor}20; color: ${notification.typeColor};">
            <i class="bi ${notification.typeIcon}"></i>
        </div>
        <div class="notification-toast-content">
            <div class="notification-toast-title">${escapeHtml(notification.title)}</div>
            <div class="notification-toast-message">${escapeHtml(notification.message)}</div>
        </div>
        <button class="notification-toast-close" onclick="this.parentElement.remove()">
            <i class="bi bi-x"></i>
        </button>
    `;

    container.appendChild(toast);

    // Animar entrada
    requestAnimationFrame(() => {
        toast.classList.add('show');
    });

    // Auto-cerrar
    setTimeout(() => {
        toast.classList.remove('show');
        setTimeout(() => toast.remove(), 300);
    }, 5000);
}

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}
