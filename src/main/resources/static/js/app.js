/**
 * Mini Jira - Shared Core Application Script (app.js)
 * Quản lý xác thực (Auth), tự động refresh token, gọi API an toàn (fetchApi),
 * xử lý danh sách đen token (TOKEN_BLACKLISTED) và chuyển đổi giao diện sáng/tối (Theme).
 */

const API_BASE_URL = '/api';

/**
 * Đối tượng quản lý trạng thái đăng nhập và token của người dùng
 */
const Auth = {
    getToken: () => localStorage.getItem('access_token'),
    setToken: (token) => localStorage.setItem('access_token', token),
    getRefreshToken: () => localStorage.getItem('refresh_token'),
    setRefreshToken: (token) => localStorage.setItem('refresh_token', token),
    removeRefreshToken: () => localStorage.removeItem('refresh_token'),
    removeToken: () => {
        localStorage.removeItem('access_token');
        localStorage.removeItem('refresh_token');
        localStorage.removeItem('current_user');
    },
    isAuthenticated: () => !!Auth.getToken(),
    getUser: () => {
        try {
            const userStr = localStorage.getItem('current_user');
            return userStr ? JSON.parse(userStr) : null;
        } catch (e) {
            return null;
        }
    },
    setUser: (user) => localStorage.setItem('current_user', JSON.stringify(user)),
    isAdmin: () => {
        const user = Auth.getUser();
        return user && user.role === 'ADMIN';
    },

    /**
     * Đăng xuất người dùng:
     * Gửi Refresh Token và Access Token (nếu còn) lên server để:
     * 1. Revoke Refresh Token trong DB.
     * 2. Đưa Access Token vào Blacklist (RAM + DB) để chặn sử dụng lại.
     */
    logout: async () => {
        const rt = Auth.getRefreshToken();
        const at = Auth.getToken();
        if (rt || at) {
            try {
                const headers = { 'Content-Type': 'application/json' };
                if (at) {
                    headers['Authorization'] = `Bearer ${at}`;
                }
                await fetch(`${API_BASE_URL}/auth/logout`, {
                    method: 'POST',
                    headers: headers,
                    body: JSON.stringify({ refreshToken: rt || '' })
                });
            } catch (e) {
                /* Bỏ qua lỗi mạng - vẫn dọn dẹp local storage */
            }
        }
        Auth.removeToken();
        window.location.href = 'login.html';
    }
};

/**
 * Tự động dùng refresh token để cấp access token mới khi token cũ hết hạn
 */
let _isRefreshing = false;
let _refreshQueue = [];

async function refreshAccessToken() {
    const rt = Auth.getRefreshToken();
    if (!rt) throw new Error('No refresh token');

    const response = await fetch(`${API_BASE_URL}/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken: rt })
    });

    if (!response.ok) {
        Auth.removeToken();
        window.location.href = 'login.html?sessionExpired=true';
        throw new Error('Session expired');
    }

    const data = await response.json();
    Auth.setToken(data.accessToken);
    if (data.refreshToken) {
        Auth.setRefreshToken(data.refreshToken); // rotate refresh token
    }
    return data.accessToken;
}

/**
 * Hàm gọi API dùng chung:
 * - Tự động đính kèm Authorization header với Bearer token.
 * - Tự động bắt lỗi 401: nếu token hết hạn thì refresh token và retry request 1 lần.
 * - Nếu token bị blacklist, lệch version, hoặc tài khoản bị khóa/xóa: logout tức thì.
 */
async function fetchApi(endpoint, options = {}) {
    const headers = {
        ...(options.headers || {})
    };

    if (!(options.body instanceof FormData)) {
        headers['Content-Type'] = headers['Content-Type'] || 'application/json';
    }

    if (Auth.isAuthenticated()) {
        headers['Authorization'] = `Bearer ${Auth.getToken()}`;
    }

    const response = await fetch(`${API_BASE_URL}${endpoint}`, { ...options, headers });
    const data = await response.json().catch(() => ({}));

    // Xử lý lỗi 401 Unauthorized (ngoại trừ các endpoint auth công khai)
    if (response.status === 401 && !endpoint.startsWith('/auth/')) {
        // Kiểm tra các lỗi nghiêm trọng không thể refresh
        const isAccountIssue = data.code === 'ACCOUNT_LOCKED' ||
            data.code === 'ACCOUNT_DISABLED' ||
            data.code === 'TOKEN_VERSION_MISMATCH' ||
            data.code === 'TOKEN_BLACKLISTED' ||
            data.code === 'USER_NOT_FOUND' ||
            (data.message && (
                data.message.includes('khóa') ||
                data.message.includes('kích hoạt') ||
                data.message.includes('hết hiệu lực') ||
                data.message.includes('đăng xuất') ||
                data.message.includes('locked') ||
                data.message.includes('disabled')
            ));

        if (isAccountIssue) {
            Auth.removeToken();
            window.location.href = 'login.html?sessionExpired=true';
            throw new Error(data.message || 'Phiên đăng nhập đã hết hiệu lực');
        }

        // Nếu client chưa từng đăng nhập, để trang gọi tự xử lý (ví dụ: trang xem public)
        if (!Auth.isAuthenticated()) {
            throw new Error(data.message || 'Unauthorized');
        }

        // Token hết hạn thông thường -> Thử làm mới token và gửi lại request
        try {
            await refreshAccessToken();
        } catch (e) {
            console.warn("Refresh token thất bại, chuyển hướng đăng nhập:", e);
            Auth.removeToken();
            window.location.href = 'login.html?sessionExpired=true';
            throw e;
        }

        // Retry request với token mới
        const retryHeaders = { ...headers, 'Authorization': `Bearer ${Auth.getToken()}` };
        const retry = await fetch(`${API_BASE_URL}${endpoint}`, { ...options, headers: retryHeaders });
        const retryData = await retry.json().catch(() => ({}));
        if (!retry.ok) {
            let errMsg = retryData.message;
            if (retryData.fieldErrors && typeof retryData.fieldErrors === 'object' && Object.keys(retryData.fieldErrors).length > 0) {
                errMsg = Object.values(retryData.fieldErrors).join(', ');
            }
            throw new Error(errMsg || (retry.status === 403 ? 'Access Denied: You do not have permission for this resource.' : 'Something went wrong'));
        }
        return retryData;
    }

    if (!response.ok) {
        let errMsg = data.message;
        if (data.fieldErrors && typeof data.fieldErrors === 'object' && Object.keys(data.fieldErrors).length > 0) {
            errMsg = Object.values(data.fieldErrors).join(', ');
        }
        const err = new Error(errMsg || (response.status === 403 ? 'Access Denied: You do not have permission for this resource.' : 'Something went wrong'));
        err.status = response.status;
        err.requiresCaptcha = data.requiresCaptcha;
        err.remainingAttempts = data.remainingAttempts;
        err.data = data;
        throw err;
    }

    return data;
}

/**
 * Quản lý Theme (Dark/Light mode)
 */
function initTheme() {
    const savedTheme = localStorage.getItem('theme') || 'light';
    applyTheme(savedTheme);
}

function applyTheme(theme) {
    if (theme === 'dark') {
        document.documentElement.classList.add('dark');
        document.documentElement.setAttribute('data-theme', 'dark');
    } else {
        document.documentElement.classList.remove('dark');
        document.documentElement.setAttribute('data-theme', 'light');
    }
    localStorage.setItem('theme', theme);
}

function toggleTheme() {
    const currentTheme = localStorage.getItem('theme') || 'light';
    const newTheme = currentTheme === 'dark' ? 'light' : 'dark';
    applyTheme(newTheme);
}

/**
 * Hàm cập nhật UI thông tin người dùng lên Sidebar / Header
 */
function updateSidebarUserUI(user) {
    if (!user) return;

    const displayName = user.fullName || user.email || 'User';

    // Cập nhật tên hiển thị ở sidebar (hỗ trợ cả userDisplayName và sidebarUserName)
    const nameEls = [
        document.getElementById('userDisplayName'),
        document.getElementById('sidebarUserName')
    ].filter(Boolean);
    nameEls.forEach(el => {
        el.textContent = displayName;
        el.title = user.email || displayName;
    });

    // Cập nhật vai trò / role
    const roleEls = [
        document.getElementById('userRoleBadge'),
        document.getElementById('sidebarUserRole')
    ].filter(Boolean);
    roleEls.forEach(el => {
        el.textContent = user.role || 'MEMBER';
    });

    // Cập nhật email nếu có element riêng
    const emailEls = [
        document.getElementById('userEmail'),
        document.getElementById('sidebarUserEmail')
    ].filter(Boolean);
    emailEls.forEach(el => {
        el.textContent = user.email || '';
    });

    // Cập nhật avatar ở sidebar (hỗ trợ cả userAvatar và sidebarUserAvatar)
    const avatarEls = [
        document.getElementById('userAvatar'),
        document.getElementById('sidebarUserAvatar')
    ].filter(Boolean);

    avatarEls.forEach(el => {
        if (user.avatarUrl) {
            el.innerHTML = `<img src="${user.avatarUrl}" class="w-full h-full rounded-full object-cover" alt="avatar" />`;
            el.classList.remove('bg-primary');
        } else {
            const initials = displayName.substring(0, 2).toUpperCase();
            el.textContent = initials;
            el.classList.add('bg-primary');
        }
    });

    // Ẩn/hiện các phần tử admin-only
    const adminElements = document.querySelectorAll('.admin-only');
    adminElements.forEach(el => {
        if (user.role === 'ADMIN') {
            el.classList.remove('hidden');
        } else {
            el.classList.add('hidden');
        }
    });
}

/**
 * Đồng bộ thông tin người dùng lên giao diện Sidebar / Header
 */
async function syncCurrentUser() {
    if (!Auth.isAuthenticated()) return;

    // 1. Cập nhật tức thì từ cache localStorage để không bị giật giao diện
    const cachedUser = Auth.getUser();
    if (cachedUser) {
        updateSidebarUserUI(cachedUser);
    }

    // 2. Tự động fetch thông tin mới nhất từ server
    try {
        const user = await fetchApi('/auth/me');
        if (user) {
            Auth.setUser(user);
            updateSidebarUserUI(user);
        }
    } catch (e) {
        console.warn("Không thể đồng bộ thông tin user từ server:", e);
    }
}

// Khởi tạo theme khi file được nạp
initTheme();

/**
 * Tiện ích hiển thị và quản lý nhãn (LabelUtils)
 * Hỗ trợ song song bảng màu Light Mode chuẩn Jira và Dark Mode dịu mắt, sang trọng
 */
const LabelUtils = {
    // Kiểm tra xem giao diện hiện tại có đang ở chế độ Tối (Dark Mode) hay không
    isDarkMode: function () {
        return document.documentElement.classList.contains('dark') ||
               document.documentElement.getAttribute('data-theme') === 'dark' ||
               localStorage.getItem('theme') === 'dark';
    },

    // Bản đồ màu sắc định danh chuẩn cho từng nhãn trong hệ thống (Light & Dark)
    KNOWN_COLORS: {
        'bug': {
            light: { bg: '#FEE2E2', text: '#DC2626', border: '#FCA5A5' },
            dark:  { bg: '#3B181E', text: '#F87171', border: '#7F1D1D' }
        },
        'feature': {
            light: { bg: '#DCFCE7', text: '#15803D', border: '#86EFAC' },
            dark:  { bg: '#132D21', text: '#4ADE80', border: '#166534' }
        },
        'backend': {
            light: { bg: '#EDE9FE', text: '#6D28D9', border: '#DDD6FE' },
            dark:  { bg: '#251B3A', text: '#C084FC', border: '#581C87' }
        },
        'frontend': {
            light: { bg: '#E0F2FE', text: '#0284C7', border: '#BAE6FD' },
            dark:  { bg: '#102A43', text: '#38BDF8', border: '#0369A1' }
        },
        'security': {
            light: { bg: '#FEF3C7', text: '#B45309', border: '#FDE68A' },
            dark:  { bg: '#362415', text: '#FBBF24', border: '#92400E' }
        },
        'database': {
            light: { bg: '#CCFBF1', text: '#0F766E', border: '#99F6E4' },
            dark:  { bg: '#112F2E', text: '#2DD4BF', border: '#115E59' }
        },
        'devops': {
            light: { bg: '#FFEDD5', text: '#C2410C', border: '#FED7AA' },
            dark:  { bg: '#381E15', text: '#FB923C', border: '#9A3412' }
        },
        'ui': {
            light: { bg: '#E0E7FF', text: '#4338CA', border: '#C7D2FE' },
            dark:  { bg: '#1E1E3F', text: '#818CF8', border: '#3730A3' }
        },
        'enhancement': {
            light: { bg: '#DCFCE7', text: '#15803D', border: '#86EFAC' },
            dark:  { bg: '#132D21', text: '#4ADE80', border: '#166534' }
        },
        'documentation': {
            light: { bg: '#F3F4F6', text: '#374151', border: '#D1D5DB' },
            dark:  { bg: '#1E293B', text: '#94A3B8', border: '#334155' }
        },
        'testing': {
            light: { bg: '#FCE7F3', text: '#BE185D', border: '#FBCFE8' },
            dark:  { bg: '#371829', text: '#F472B6', border: '#831843' }
        }
    },

    // Bảng màu dự phòng theo hash khi có nhãn tùy chỉnh mới (Light & Dark)
    PALETTES: [
        {
            light: { bg: '#E0F2FE', text: '#0284C7', border: '#BAE6FD' },
            dark:  { bg: '#102A43', text: '#38BDF8', border: '#0369A1' }
        },
        {
            light: { bg: '#DCFCE7', text: '#15803D', border: '#86EFAC' },
            dark:  { bg: '#132D21', text: '#4ADE80', border: '#166534' }
        },
        {
            light: { bg: '#EDE9FE', text: '#6D28D9', border: '#DDD6FE' },
            dark:  { bg: '#251B3A', text: '#C084FC', border: '#581C87' }
        },
        {
            light: { bg: '#FEF3C7', text: '#B45309', border: '#FDE68A' },
            dark:  { bg: '#362415', text: '#FBBF24', border: '#92400E' }
        },
        {
            light: { bg: '#FEE2E2', text: '#DC2626', border: '#FCA5A5' },
            dark:  { bg: '#3B181E', text: '#F87171', border: '#7F1D1D' }
        },
        {
            light: { bg: '#CCFBF1', text: '#0F766E', border: '#99F6E4' },
            dark:  { bg: '#112F2E', text: '#2DD4BF', border: '#115E59' }
        },
        {
            light: { bg: '#FFEDD5', text: '#C2410C', border: '#FED7AA' },
            dark:  { bg: '#381E15', text: '#FB923C', border: '#9A3412' }
        },
        {
            light: { bg: '#E0E7FF', text: '#4338CA', border: '#C7D2FE' },
            dark:  { bg: '#1E1E3F', text: '#818CF8', border: '#3730A3' }
        },
        {
            light: { bg: '#FCE7F3', text: '#BE185D', border: '#FBCFE8' },
            dark:  { bg: '#371829', text: '#F472B6', border: '#831843' }
        },
        {
            light: { bg: '#F3F4F6', text: '#374151', border: '#D1D5DB' },
            dark:  { bg: '#1E293B', text: '#94A3B8', border: '#334155' }
        }
    ],

    hashString: function (str) {
        let hash = 0;
        if (!str || str.length === 0) return 0;
        for (let i = 0; i < str.length; i++) {
            const char = str.charCodeAt(i);
            hash = ((hash << 5) - hash) + char;
            hash |= 0;
        }
        return Math.abs(hash);
    },

    /**
     * Lấy style màu sắc theo tên nhãn.
     * Tự động trả về màu chuẩn theo theme hiện tại (Light hoặc Dark).
     */
    getColorStyle: function (name) {
        const key = (name || '').trim().toLowerCase();
        let colorPair = LabelUtils.KNOWN_COLORS[key];
        if (!colorPair) {
            const index = LabelUtils.hashString(key) % LabelUtils.PALETTES.length;
            colorPair = LabelUtils.PALETTES[index];
        }

        const isDark = LabelUtils.isDarkMode();
        const active = isDark ? colorPair.dark : colorPair.light;

        return {
            bg: active.bg,
            text: active.text,
            border: active.border,
            hexBg: active.bg,
            hexText: active.text,
            hexBorder: active.border,
            light: colorPair.light,
            dark: colorPair.dark
        };
    },

    /**
     * Render badge nhãn chuẩn hỗ trợ phản hồi mượt mà cả Light Mode và Dark Mode
     */
    renderBadge: function (label, options = {}) {
        if (!label) return '';
        const name = typeof label === 'string' ? label : (label.name || '');
        if (!name) return '';

        const style = LabelUtils.getColorStyle(name);
        const size = options.size || 'xs';
        const sizeClass = size === 'xs'
            ? 'text-[11px] px-1.5 py-0.5'
            : (size === 'sm' ? 'text-xs px-2 py-0.5' : 'text-sm px-2.5 py-1');

        const light = style.light;
        const dark = style.dark;

        return `<span class="label-chip inline-flex items-center font-medium rounded border ${sizeClass} select-none shrink-0"
                      style="--lbl-bg: ${light.bg}; --lbl-text: ${light.text}; --lbl-border: ${light.border}; --lbl-dark-bg: ${dark.bg}; --lbl-dark-text: ${dark.text}; --lbl-dark-border: ${dark.border}; background-color: ${style.bg}; color: ${style.text}; border-color: ${style.border};"
                      title="${name}">
            <span class="truncate max-w-[120px]">${name}</span>
        </span>`;
    }
};
