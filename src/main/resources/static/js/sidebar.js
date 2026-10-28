/**
 * Mini Jira - Shared Modular Sidebar Component
 * Đồng bộ toàn bộ thanh Sidebar cho tất cả màn hình
 */
const Sidebar = {
    // Xác định trang hiện tại để kích hoạt active state
    getActiveKey: function () {
        const path = window.location.pathname.toLowerCase();
        if (path.includes('dashboard') || path.includes('transition')) return 'dashboard';
        if (path.includes('projects') || path.includes('board')) return 'projects';
        if (path.includes('issue')) return 'issues';
        if (path.includes('users')) return 'users';
        if (path.includes('settings')) return 'settings';
        return 'projects';
    },

    render: function (targetElementId = 'appSidebar', forcedActiveKey = null) {
        // Bỏ qua nếu là trang auth (login, register) hoặc khách chưa đăng nhập
        if (document.querySelector('.auth-layout') || !Auth.isAuthenticated()) {
            const target = document.getElementById(targetElementId);
            if (target) target.classList.add('hidden');
            return;
        }

        const activeKey = forcedActiveKey || this.getActiveKey();

        const menuItems = [
            {
                key: 'dashboard',
                href: 'dashboard.html',
                icon: 'dashboard',
                label: 'Dashboard',
                i18nKey: 'nav.dashboard',
                adminOnly: false
            },
            {
                key: 'projects',
                href: 'projects.html',
                icon: 'account_tree',
                label: 'Projects',
                i18nKey: 'nav.projects',
                adminOnly: false
            },
            {
                key: 'issues',
                href: 'issues.html',
                icon: 'task_alt',
                label: 'Issues',
                i18nKey: 'nav.issues',
                adminOnly: false
            },
            {
                key: 'users',
                href: 'users.html',
                icon: 'admin_panel_settings',
                label: 'Users',
                i18nKey: 'nav.users',
                adminOnly: true
            },
            {
                key: 'settings',
                href: 'settings.html',
                icon: 'settings',
                label: 'Settings',
                i18nKey: 'nav.settings',
                adminOnly: false
            }
        ];

        const activeClass = "bg-primary/10 text-primary border-l-4 border-primary font-semibold active:scale-95";
        const inactiveClass = "text-text-subtle hover:bg-surface-container-low hover:text-text-main font-medium active:scale-95 transition-colors";

        const navHtml = menuItems.map(item => {
            const isActive = item.key === activeKey;
            const itemClass = isActive ? activeClass : inactiveClass;
            const adminClass = item.adminOnly ? 'admin-only hidden' : '';

            return `
                <a href="${item.href}" class="${adminClass} flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm transition-all duration-200 ${itemClass}">
                    <span class="material-symbols-outlined text-[20px]">${item.icon}</span>
                    <span data-i18n="${item.i18nKey}">${item.label}</span>
                </a>
            `;
        }).join('');

        const currentLang = localStorage.getItem('app_lang') || 'en';

        const currentUser = (typeof Auth !== 'undefined' && typeof Auth.getUser === 'function') ? Auth.getUser() : null;
        const displayName = currentUser ? (currentUser.fullName || currentUser.email || 'User') : 'User';
        const displayRole = currentUser ? (currentUser.role || 'MEMBER') : 'MEMBER';
        const initials = displayName.substring(0, 2).toUpperCase();
        const avatarHtml = (currentUser && currentUser.avatarUrl) 
            ? `<img src="${currentUser.avatarUrl}" class="w-full h-full rounded-full object-cover" alt="avatar" />`
            : initials;
        const avatarBgClass = (currentUser && currentUser.avatarUrl) ? '' : 'bg-primary';

        const sidebarHtml = `
        <aside id="mainAppSidebar" class="bg-surface-container-lowest border-r border-border shadow-sm w-[240px] h-screen fixed left-0 top-0 flex flex-col py-6 px-4 z-20 transition-colors duration-200">
            <!-- Brand Logo -->
            <a href="projects.html" class="flex items-center gap-3 mb-8 px-2 group cursor-pointer text-inherit no-underline">
                <div class="w-8 h-8 bg-primary rounded-lg flex items-center justify-center text-white shadow-sm group-hover:scale-105 transition-transform flex-shrink-0">
                    <span class="material-symbols-outlined text-[20px]" style="font-variation-settings: 'FILL' 1;">dataset</span>
                </div>
                <div>
                    <h1 class="text-[18px] font-bold text-primary leading-tight" style="font-size: 18px; font-weight: 700;">Mini Jira</h1>
                    <p class="text-xs text-text-subtle">Task Management</p>
                </div>
            </a>

            <!-- Action Button -->
            <button onclick="if(typeof openCreateProjectModal === 'function') openCreateProjectModal(); else window.location.href='projects.html?action=create'" class="w-full h-10 bg-primary hover:bg-primary-container text-white text-sm font-semibold rounded-lg flex items-center justify-center gap-2 mb-6 transition-all duration-200 shadow-sm active:scale-95 cursor-pointer">
                <span class="material-symbols-outlined text-[18px]">add</span>
                <span data-i18n="projects.createBtn">Create Project</span>
            </button>

            <!-- Navigation Links -->
            <nav class="flex-1 flex flex-col gap-1.5 overflow-y-auto">
                ${navHtml}
            </nav>

            <!-- Bottom Controls & User Profile -->
            <div class="mt-auto flex flex-col gap-3 pt-4 border-t border-border flex-shrink-0">
                <!-- User Profile Card -->
                <div class="flex items-center gap-2.5 px-2.5 py-2 rounded-lg bg-surface-container-low/60 border border-border/60">
                    <div class="w-8 h-8 rounded-full ${avatarBgClass} flex items-center justify-center text-white font-bold text-xs shadow-sm flex-shrink-0 overflow-hidden" id="userAvatar">${avatarHtml}</div>
                    <div class="flex-1 min-w-0">
                        <div class="text-xs font-semibold text-text-main truncate" id="userDisplayName" title="${currentUser ? (currentUser.email || displayName) : ''}">${displayName}</div>
                        <div class="text-[10px] text-text-subtle font-mono uppercase truncate" id="userRoleBadge">${displayRole}</div>
                    </div>
                </div>

                <!-- Utilities Row: Language Selector, Theme Switcher, Logout -->
                <div class="flex items-center justify-between gap-1.5 px-0.5">
                    <!-- Language Selector -->
                    <div class="relative flex-1">
                        <select onchange="I18n.setLanguage(this.value)" class="lang-select w-full bg-surface-container-low text-text-main text-[11px] font-medium border border-border rounded-lg px-2 py-1.5 pr-5 focus:outline-none focus:border-primary cursor-pointer appearance-none">
                            <option value="en" ${currentLang === 'en' ? 'selected' : ''}>🇬🇧 EN</option>
                            <option value="vi" ${currentLang === 'vi' ? 'selected' : ''}>🇻🇳 VI</option>
                        </select>
                        <span class="material-symbols-outlined absolute right-1.5 top-1/2 -translate-y-1/2 pointer-events-none text-text-subtle text-[14px]">expand_more</span>
                    </div>

                    <!-- Theme Toggle -->
                    <button onclick="toggleTheme()" title="Toggle Dark/Light Mode" class="p-1.5 rounded-lg border border-border bg-surface-container-low hover:bg-surface-container-high text-text-subtle hover:text-primary transition-colors cursor-pointer flex items-center justify-center">
                        <span class="material-symbols-outlined text-[18px]">dark_mode</span>
                    </button>

                    <!-- Logout Button -->
                    <button onclick="Sidebar.openLogoutModal()" title="Logout" class="p-1.5 rounded-lg border border-border bg-surface-container-low hover:bg-danger/10 text-danger transition-colors cursor-pointer flex items-center justify-center">
                        <span class="material-symbols-outlined text-[18px]">logout</span>
                    </button>
                </div>
            </div>
        </aside>
        `;

        const targetEl = document.getElementById(targetElementId);
        if (targetEl) {
            targetEl.outerHTML = sidebarHtml;
        } else {
            const existingAside = document.querySelector('aside');
            if (existingAside) {
                existingAside.outerHTML = sidebarHtml;
            } else {
                document.body.insertAdjacentHTML('afterbegin', sidebarHtml);
            }
        }

        // Tự động gắn Logout Confirmation Modal vào body nếu chưa có
        if (!document.getElementById('logoutConfirmModal')) {
            const logoutModalHtml = `
            <div id="logoutConfirmModal" class="fixed inset-0 bg-black/60 hidden flex items-center justify-center z-50 p-4 transition-all duration-200" onclick="if(event.target === this) Sidebar.closeLogoutModal()">
                <div class="bg-surface-container-lowest rounded-2xl shadow-2xl w-full max-w-sm p-6 border border-border animate-fade-in relative text-center">
                    <!-- Close button -->
                    <button type="button" onclick="Sidebar.closeLogoutModal()" class="absolute right-4 top-4 text-text-subtle hover:text-text-main p-1 rounded-lg hover:bg-surface-container-low cursor-pointer transition-colors">
                        <span class="material-symbols-outlined text-[20px]">close</span>
                    </button>

                    <!-- Icon -->
                    <div class="w-14 h-14 rounded-full bg-danger/10 text-danger mx-auto flex items-center justify-center mb-4 border border-danger/20 shadow-sm">
                        <span class="material-symbols-outlined text-3xl">logout</span>
                    </div>

                    <!-- Title -->
                    <h3 class="font-headline-sm text-lg font-bold text-text-main mb-2" data-i18n="logout.modal.title">
                        Xác nhận đăng xuất
                    </h3>

                    <!-- Description -->
                    <p class="text-xs text-text-subtle leading-relaxed mb-6" data-i18n="logout.modal.desc">
                        Bạn có chắc chắn muốn đăng xuất khỏi tài khoản của mình không?
                    </p>

                    <!-- Action buttons -->
                    <div class="flex items-center gap-3">
                        <button type="button" onclick="Sidebar.closeLogoutModal()" class="flex-1 py-2.5 px-4 rounded-xl border border-border bg-surface-container-low hover:bg-surface-container-high text-xs font-semibold text-text-main transition-all cursor-pointer" data-i18n="logout.modal.cancel">
                            Hủy bỏ
                        </button>
                        <button type="button" onclick="Auth.logout()" class="flex-1 py-2.5 px-4 rounded-xl bg-danger hover:bg-opacity-90 text-white text-xs font-semibold shadow-sm hover:shadow active:scale-95 transition-all cursor-pointer" data-i18n="logout.modal.confirm">
                            Đăng xuất
                        </button>
                    </div>
                </div>
            </div>
            `;
            document.body.insertAdjacentHTML('beforeend', logoutModalHtml);
        }

        // Kích hoạt lại ngôn ngữ & phân quyền cho sidebar vừa render
        if (typeof I18n !== 'undefined' && typeof I18n.applyI18n === 'function') {
            I18n.applyI18n();
        }
        if (typeof syncCurrentUser === 'function') {
            syncCurrentUser();
        }
        if (typeof initTheme === 'function') {
            initTheme();
        }
    },

    openLogoutModal: function () {
        const modal = document.getElementById('logoutConfirmModal');
        if (modal) {
            modal.classList.remove('hidden');
            if (typeof I18n !== 'undefined' && typeof I18n.applyI18n === 'function') {
                I18n.applyI18n();
            }
        } else {
            if (confirm(typeof I18n !== 'undefined' ? I18n.t('logout.modal.desc') : 'Bạn có chắc chắn muốn đăng xuất không?')) {
                Auth.logout();
            }
        }
    },

    closeLogoutModal: function () {
        const modal = document.getElementById('logoutConfirmModal');
        if (modal) {
            modal.classList.add('hidden');
        }
    }
};

// Đóng modal khi bấm Escape
document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') {
        const modal = document.getElementById('logoutConfirmModal');
        if (modal && !modal.classList.contains('hidden')) {
            Sidebar.closeLogoutModal();
        }
    }
});

// Tự động render sidebar khi load
document.addEventListener('DOMContentLoaded', () => {
    Sidebar.render();
});
