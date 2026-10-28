/**
 * Mini Jira - Multi-language (i18n) Module
 */
const I18n = {
    currentLang: localStorage.getItem('app_lang') || 'en',
    dictionaries: {
        en: {
            "nav.dashboard": "Dashboard",
            "nav.projects": "Projects",
            "nav.issues": "Issues",
            "nav.users": "Users",
            "nav.settings": "Settings",
            "nav.help": "Help",
            "nav.logout": "Logout",
            "nav.createProject": "Create Project",

            "header.search": "Search projects, issues, or people...",
            "header.user": "User",

            "auth.welcomeBack": "Welcome Back",
            "auth.loginSubtitle": "Login to your Mini Jira account to manage your projects.",
            "auth.email": "Email Address",
            "auth.password": "Password",
            "auth.signIn": "Sign In",
            "auth.dontHaveAccount": "Don't have an account?",
            "auth.signUp": "Sign up",
            "auth.createAccount": "Create Account",
            "auth.registerSubtitle": "Join Mini Jira to start managing your projects efficiently.",
            "auth.fullName": "Full Name",
            "auth.alreadyHaveAccount": "Already have an account?",
            "auth.signingUp": "Signing up...",
            "auth.signingIn": "Signing in...",
            "auth.forgotPassword": "Forgot password?",
            "auth.forgotPasswordTitle": "Forgot Password",
            "auth.forgotPasswordSubtitle": "Enter your email address and we'll send you a link to reset your password.",
            "auth.sendResetLink": "Send Reset Link",
            "auth.backToLogin": "Back to Sign In",
            "auth.resetPasswordTitle": "Reset Password",
            "auth.resetPasswordSubtitle": "Enter your new password below.",
            "auth.newPassword": "New Password",
            "auth.confirmNewPassword": "Confirm New Password",
            "auth.updatePassword": "Reset Password",

            "projects.title": "Projects",
            "projects.subtitle": "Manage and track all your active initiatives.",
            "projects.createBtn": "Create Project",
            "projects.all": "All Projects",
            "projects.my": "My Projects",
            "projects.searchPlaceholder": "Filter projects by name or key...",
            "projects.table.key": "Key",
            "projects.table.name": "Project Name",
            "projects.table.owner": "Lead / Owner",
            "projects.table.issues": "Issues",
            "projects.table.created": "Created Date",
            "projects.table.actions": "Actions",
            "projects.table.viewBoard": "View Board",
            "projects.table.viewIssues": "View Issues",
            "projects.table.loading": "Loading projects...",
            "projects.table.empty": "No projects found.",
            "projects.yourProject": "Your Project",
            "projects.count": "{count} project(s)",
            "projects.emptyMy": "You haven't created any projects yet. Click Create Project to get started!",
            "projects.creator": "Creator",
            "projects.modal.title": "Create New Project",
            "projects.modal.key": "Project Key",
            "projects.modal.keyPlaceholder": "e.g. PROJ, DEV",
            "projects.modal.name": "Project Name",
            "projects.modal.namePlaceholder": "e.g. Task Management System",
            "projects.modal.desc": "Description",
            "projects.modal.descPlaceholder": "Describe the goals and scope of this project...",
            "projects.modal.cancel": "Cancel",
            "projects.modal.create": "Create Project",

            "dashboard.title": "Dashboard",
            "dashboard.subtitle": "Welcome back. Here's a summary of your workspace.",
            "dashboard.selectProject": "Select Project",
            "dashboard.totalProjects": "Total Projects",
            "dashboard.todo": "To Do",
            "dashboard.inProgress": "In Progress",
            "dashboard.review": "In Review",
            "dashboard.done": "Done",
            "dashboard.distribution": "Issue Distribution",
            "dashboard.statusOverview": "Status Overview",
            "dashboard.noData": "No Data",
            "dashboard.recentProjects": "Recent Projects",
            "dashboard.recentIssues": "Recent Issues",

            "issues.title": "Issues Explorer",
            "issues.subtitle": "Search, filter, and track all tasks across your projects.",
            "issues.createBtn": "Create Issue",
            "issues.export.btn": "Export Excel",
            "issues.export.currentFields": "Current fields",
            "issues.export.currentFieldsDesc": "Export only visible columns in the table",
            "issues.export.allFields": "All fields",
            "issues.export.allFieldsDesc": "Export all 18 detailed issue fields",
            "issues.import.btn": "Import Excel",
            "issues.import.modalTitle": "Import Issues from Excel",
            "issues.import.downloadTemplate": "Download Excel Template",
            "issues.import.dropzoneText": "Drag and drop .xlsx file here or click to browse",
            "issues.import.dropzoneHint": "Supports .xlsx up to 500 rows",
            "issues.import.selectedFile": "Selected file:",
            "issues.import.uploadBtn": "Start Import",
            "issues.import.uploading": "Importing data...",
            "issues.import.resultSuccess": "Successfully imported {count} issue(s)!",
            "issues.import.resultPartial": "Successfully imported {success} issue(s), {failed} row(s) failed.",
            "issues.import.errorList": "Error details:",
            "issues.searchPlaceholder": "Filter issues by key or title...",
            "issues.filter.project": "Project:",
            "issues.filter.loadingProjects": "Loading projects...",
            "issues.filter.noProjects": "No projects available",
            "issues.filter.status": "Status:",
            "issues.filter.priority": "Priority:",
            "issues.filter.allStatuses": "All Statuses",
            "issues.filter.allPriorities": "All Priorities",
            "issues.filter.myIssues": "My Issues",
            "issues.filter.overdue": "Overdue",
            "issues.filter.showing": "Showing",
            "issues.filter.issuesCount": "issue(s)",
            "issues.table.key": "Key",
            "issues.table.title": "Title",
            "issues.table.status": "Status",
            "issues.table.priority": "Priority",
            "issues.table.assignee": "Assignee",
            "issues.table.dueDate": "Due Date",
            "issues.table.actions": "Actions",
            "issues.table.loading": "Loading issues...",
            "issues.table.empty": "No issues match your criteria in this project.",
            "issues.table.noProjectPrompt": "You have no projects yet. Create a project first.",
            "issues.table.unassigned": "Unassigned",
            "issues.table.viewDetail": "View Detail",
            "issues.modal.title": "Create Issue",
            "issues.modal.project": "Project",
            "issues.modal.summary": "Title / Summary",
            "issues.modal.summaryPlaceholder": "e.g. Fix authentication token expiration",
            "issues.modal.desc": "Description",
            "issues.modal.descPlaceholder": "Add detailed requirements or steps to reproduce...",
            "issues.modal.priority": "Priority",
            "issues.modal.dueDate": "Due Date",
            "issues.modal.assignee": "Assignee",
            "issues.modal.cancel": "Cancel",
            "issues.modal.create": "Create Issue",
            "issues.modal.noticePriority": "Only Project Manager or Admin can change priority.",
            "issues.modal.noticeDueDate": "Only Project Manager or Admin can change due date.",

            "issueDetail.title": "Issue Details",
            "issueDetail.breadcrumbs.projects": "Projects",
            "issueDetail.description": "Description",
            "issueDetail.noDescription": "No description provided.",
            "issueDetail.attachments": "Attachments",
            "issueDetail.comments": "Comments",
            "issueDetail.addComment": "Add a comment...",
            "issueDetail.saveComment": "Save",
            "issueDetail.status": "Status",
            "issueDetail.priority": "Priority",
            "issueDetail.assignee": "Assignee",
            "issueDetail.dueDate": "Due Date / Deadline",
            "issueDetail.reporter": "Reporter",
            "issueDetail.created": "Created:",
            "issueDetail.updated": "Updated:",
            "issueDetail.unassigned": "Unassigned",
            "issueDetail.notice.priority": "Only Project Manager or Admin can change priority.",
            "issueDetail.notice.assignee": "Only Project Manager or Admin can change assignee.",
            "issueDetail.notice.dueDate": "Only Project Manager or Admin can change due date.",
            "issueDetail.notice.status": "Only reporter, Project Manager, or assignee can change status.",
            "issueDetail.modal.editTitle": "Edit Issue",
            "issueDetail.modal.saveChanges": "Save changes",
            "issueDetail.alert.priorityRestricted": "Only Project Manager or Admin can change priority.",
            "issueDetail.alert.assigneeRestricted": "Only Project Manager or Admin can change assignee.",
            "issueDetail.alert.dueDateRestricted": "Only Project Manager or Admin can change due date.",
            "issueDetail.comment.contentRequired": "Please enter comment content before submitting.",
            "issueDetail.comment.textRequiredWithImage": "Please enter comment text (comment content cannot be empty even with attached images).",

            "board.title": "Project Board",
            "board.subtitle": "Manage and move issues across sprint workflows.",
            "board.breadcrumbs.projects": "Projects",
            "board.membersBtn": "Members",
            "board.newIssueBtn": "New Issue",
            "board.col.todo": "TO DO",
            "board.col.inProgress": "IN PROGRESS",
            "board.col.inReview": "IN REVIEW",
            "board.col.done": "DONE",
            "board.modal.membersTitle": "Project Members",
            "board.modal.membersSubtitle": "Manage user access and roles in this project.",
            "board.modal.currentMembers": "Current Members",
            "board.modal.addNewMember": "Add New Member",
            "board.modal.userId": "Select User",
            "board.modal.projectRole": "Project Role",
            "board.modal.addMemberBtn": "Add Member",
            "board.modal.loadingMembers": "Loading members...",
            "board.modal.noMembers": "No members added yet.",

            "status.todo": "To Do",
            "status.inProgress": "In Progress",
            "status.inReview": "In Review",
            "status.done": "Done",

            "priority.high": "High",
            "priority.medium": "Medium",
            "priority.low": "Low",
            "priority.critical": "Critical",

            "users.title": "System Users",
            "users.subtitle": "Manage system user accounts, permissions, and account statuses.",
            "users.refresh": "Refresh",
            "users.filter.role": "Role:",
            "users.filter.allRoles": "All Roles",
            "users.filter.status": "Status:",
            "users.filter.allStatuses": "All Statuses",
            "users.filter.active": "Active",
            "users.filter.locked": "Locked",
            "users.filter.unverified": "Unverified",
            "users.searchPlaceholder": "Search by name or email...",
            "users.filter.showing": "Showing",
            "users.filter.usersCount": "user(s)",
            "users.table.user": "User",
            "users.table.role": "System Role",
            "users.table.status": "Status",
            "users.table.joinedDate": "Joined Date",
            "users.table.actions": "Actions",
            "users.table.loading": "Loading users...",
            "users.table.empty": "No users found matching your criteria.",
            "users.table.you": "You",
            "users.status.active": "ACTIVE",
            "users.status.locked": "LOCKED",
            "users.actions.changeRole": "Change Role",
            "users.actions.lock": "Lock",
            "users.actions.unlock": "Unlock",
            "users.action.changeRole": "Change Role",
            "users.action.cannotSelfLock": "Cannot lock your own account",
            "users.action.cannotSelfDelete": "Cannot delete your own account",
            "users.action.lockAccount": "Lock Account",
            "users.action.unlockAccount": "Unlock Account",
            "users.action.deleteUser": "Delete User",
            "users.modal.changeRoleTitle": "Change System Role",
            "users.modal.selectRole": "Select Role",
            "users.modal.cancel": "Cancel",
            "users.modal.save": "Save Role",

            "settings.title": "Account Settings",
            "settings.subtitle": "Manage your personal profile and security preferences.",
            "settings.profile.title": "Profile Information",
            "settings.profile.active": "Active",
            "settings.profile.userId": "User ID:",
            "settings.profile.joined": "Joined on:",
            "settings.avatar.change": "Change Avatar",
            "settings.avatar.delete": "Remove Avatar",
            "settings.name.change": "Change Name",
            "settings.name.title": "Change User Name",
            "settings.name.subtitle": "Update your display name across the system.",
            "settings.name.inputLabel": "New Full Name",
            "settings.name.placeholder": "Enter full name...",
            "settings.name.saveBtn": "Save Changes",
            "settings.name.cancel": "Cancel",
            "settings.name.success": "User name updated successfully!",
            "settings.security.title": "Change Password",
            "settings.security.subtitle": "Ensure your account uses a secure, long password.",
            "settings.security.oldPassword": "Current Password",
            "settings.security.newPassword": "New Password",
            "settings.security.minLengthNotice": "Password must be at least 6 characters long.",
            "settings.security.confirmPassword": "Confirm New Password",
            "settings.security.saveBtn": "Update Password",
            "settings.session.title": "Session & Security",
            "settings.session.subtitle": "Logout from your active session on this browser.",
            "settings.session.signOut": "Sign Out",
            "settings.session.signOutDesc": "Clear credentials stored in local session",
            "settings.alert.mismatch": "New password and confirmation password do not match!",
            "settings.alert.success": "Password changed successfully!",
            "settings.alert.fail": "Failed to change password. Please check your current password.",

            "pagination.showing": "Showing {start}–{end} of {total} entries",
            "pagination.showingEmpty": "Showing 0 of 0 entries",
            "pagination.perPage": "page"
        },
        vi: {
            "nav.dashboard": "Bảng Điều Khiển",
            "nav.projects": "Dự Án",
            "nav.issues": "Công Việc",
            "nav.users": "Người Dùng",
            "nav.settings": "Cài Đặt",
            "nav.help": "Trợ Giúp",
            "nav.logout": "Đăng Xuất",
            "nav.createProject": "Tạo Dự Án",

            "header.search": "Tìm kiếm dự án, công việc, hoặc người dùng...",
            "header.user": "Người Dùng",

            "auth.welcomeBack": "Chào Mừng Trở Lại",
            "auth.loginSubtitle": "Đăng nhập vào tài khoản Mini Jira để quản lý dự án của bạn.",
            "auth.email": "Địa Chỉ Email",
            "auth.password": "Mật Khẩu",
            "auth.signIn": "Đăng Nhập",
            "auth.dontHaveAccount": "Chưa có tài khoản?",
            "auth.signUp": "Đăng ký ngay",
            "auth.createAccount": "Tạo Tài Khoản",
            "auth.registerSubtitle": "Tham gia Mini Jira để bắt đầu quản lý công việc và dự án hiệu quả.",
            "auth.fullName": "Họ và Tên",
            "auth.alreadyHaveAccount": "Đã có tài khoản?",
            "auth.signingUp": "Đang đăng ký...",
            "auth.signingIn": "Đang đăng nhập...",
            "auth.forgotPassword": "Quên mật khẩu?",
            "auth.forgotPasswordTitle": "Quên Mật Khẩu",
            "auth.forgotPasswordSubtitle": "Nhập địa chỉ email của bạn để nhận liên kết đặt lại mật khẩu.",
            "auth.sendResetLink": "Gửi link đổi mật khẩu",
            "auth.backToLogin": "Quay lại đăng nhập",
            "auth.resetPasswordTitle": "Đặt Lại Mật Khẩu",
            "auth.resetPasswordSubtitle": "Nhập mật khẩu mới của bạn bên dưới.",
            "auth.newPassword": "Mật khẩu mới",
            "auth.confirmNewPassword": "Xác nhận mật khẩu mới",
            "auth.updatePassword": "Cập nhật mật khẩu",

            "projects.title": "Danh Sách Dự Án",
            "projects.subtitle": "Quản lý và theo dõi tất cả các dự án đang hoạt động.",
            "projects.createBtn": "Tạo Dự Án",
            "projects.all": "Tất Cả Dự Án",
            "projects.my": "Dự Án Của Tôi",
            "projects.searchPlaceholder": "Lọc dự án theo tên hoặc mã...",
            "projects.table.key": "Mã Dự Án",
            "projects.table.name": "Tên Dự Án",
            "projects.table.owner": "Chủ Sở Hữu / Trưởng Nhóm",
            "projects.table.issues": "Công Việc",
            "projects.table.created": "Ngày Tạo",
            "projects.table.actions": "Hành Động",
            "projects.table.viewBoard": "Xem Bảng Kanban",
            "projects.table.viewIssues": "Xem Công Việc",
            "projects.table.loading": "Đang tải danh sách dự án...",
            "projects.table.empty": "Không tìm thấy dự án nào.",
            "projects.yourProject": "Dự án của bạn",
            "projects.count": "{count} dự án",
            "projects.emptyMy": "Bạn chưa tạo dự án nào. Bấm Tạo Dự Án để bắt đầu!",
            "projects.creator": "Người tạo",
            "projects.modal.title": "Tạo Dự Án Mới",
            "projects.modal.key": "Mã Dự Án",
            "projects.modal.keyPlaceholder": "VD: PROJ, DEV",
            "projects.modal.name": "Tên Dự Án",
            "projects.modal.namePlaceholder": "VD: Hệ thống Quản lý Công việc",
            "projects.modal.desc": "Mô Tả",
            "projects.modal.descPlaceholder": "Mô tả mục tiêu và phạm vi của dự án...",
            "projects.modal.cancel": "Hủy",
            "projects.modal.create": "Tạo Dự Án",

            "dashboard.title": "Bảng Điều Khiển",
            "dashboard.subtitle": "Chào mừng trở lại. Đây là tổng quan không gian làm việc của bạn.",
            "dashboard.selectProject": "Chọn Dự Án",
            "dashboard.totalProjects": "Tổng Số Dự Án",
            "dashboard.todo": "Cần Làm",
            "dashboard.inProgress": "Đang Xử Lý",
            "dashboard.review": "Đang Đánh Giá",
            "dashboard.done": "Hoàn Thành",
            "dashboard.distribution": "Phân Bố Công Việc",
            "dashboard.statusOverview": "Tổng Quan Trạng Thái",
            "dashboard.noData": "Không có dữ liệu",
            "dashboard.recentProjects": "Dự Án Gần Đây",
            "dashboard.recentIssues": "Công Việc Gần Đây",

            "issues.title": "Danh Sách Công Việc",
            "issues.subtitle": "Tìm kiếm, lọc và theo dõi tiến độ công việc trên tất cả các dự án.",
            "issues.createBtn": "Tạo Công Việc",
            "issues.export.btn": "Xuất Excel",
            "issues.export.currentFields": "Các cột hiển thị (Current fields)",
            "issues.export.currentFieldsDesc": "Chỉ xuất các cột đang hiển thị trên bảng",
            "issues.export.allFields": "Tất cả các cột (All fields)",
            "issues.export.allFieldsDesc": "Xuất toàn bộ 18 trường thông tin chi tiết",
            "issues.import.btn": "Import Excel",
            "issues.import.modalTitle": "Nhập Công Việc Từ Excel",
            "issues.import.downloadTemplate": "Download Excel Template",
            "issues.import.dropzoneText": "Kéo thả file .xlsx vào đây hoặc bấm để chọn",
            "issues.import.dropzoneHint": "Hỗ trợ định dạng .xlsx tối đa 500 dòng",
            "issues.import.selectedFile": "File đã chọn:",
            "issues.import.uploadBtn": "Bắt Đầu Nhập",
            "issues.import.uploading": "Đang nhập dữ liệu...",
            "issues.import.resultSuccess": "Nhập thành công {count} công việc!",
            "issues.import.resultPartial": "Nhập thành công {success} công việc, {failed} dòng bị lỗi.",
            "issues.import.errorList": "Chi tiết lỗi:",
            "issues.searchPlaceholder": "Lọc công việc theo mã hoặc tiêu đề...",
            "issues.filter.project": "Dự Án:",
            "issues.filter.loadingProjects": "Đang tải danh sách dự án...",
            "issues.filter.noProjects": "Chưa có dự án nào",
            "issues.filter.status": "Trạng Thái:",
            "issues.filter.priority": "Ưu Tiên:",
            "issues.filter.allStatuses": "Tất Cả Trạng Thái",
            "issues.filter.allPriorities": "Tất Cả Mức Ưu Tiên",
            "issues.filter.myIssues": "Việc của tôi",
            "issues.filter.overdue": "Quá hạn",
            "issues.filter.showing": "Hiển thị",
            "issues.filter.issuesCount": "công việc",
            "issues.table.key": "Mã",
            "issues.table.title": "Tiêu Đề",
            "issues.table.status": "Trạng Thái",
            "issues.table.priority": "Mức Ưu Tiên",
            "issues.table.assignee": "Người Phụ Trách",
            "issues.table.dueDate": "Hạn Hoàn Thành",
            "issues.table.actions": "Thao Tác",
            "issues.table.loading": "Đang tải danh sách công việc...",
            "issues.table.empty": "Không có công việc nào phù hợp với bộ lọc trong dự án này.",
            "issues.table.noProjectPrompt": "Bạn chưa có dự án nào. Hãy tạo dự án trước.",
            "issues.table.unassigned": "Chưa phân công",
            "issues.table.viewDetail": "Xem Chi Tiết",
            "issues.modal.title": "Tạo Công Việc Mới",
            "issues.modal.project": "Dự Án",
            "issues.modal.summary": "Tiêu Đề / Tóm Tắt",
            "issues.modal.summaryPlaceholder": "VD: Sửa lỗi hết hạn token xác thực",
            "issues.modal.desc": "Mô Tả Chi Tiết",
            "issues.modal.descPlaceholder": "Thêm yêu cầu chi tiết hoặc các bước tái hiện lỗi...",
            "issues.modal.priority": "Mức Ưu Tiên",
            "issues.modal.dueDate": "Hạn Hoàn Thành",
            "issues.modal.assignee": "Người Phụ Trách",
            "issues.modal.cancel": "Hủy",
            "issues.modal.create": "Tạo Công Việc",
            "issues.modal.noticePriority": "Chỉ Project Manager hoặc Admin mới có quyền đổi độ ưu tiên.",
            "issues.modal.noticeDueDate": "Chỉ Project Manager hoặc Admin mới có quyền đổi hạn chót.",

            "issueDetail.title": "Chi Tiết Công Việc",
            "issueDetail.breadcrumbs.projects": "Dự Án",
            "issueDetail.description": "Mô Tả",
            "issueDetail.noDescription": "Chưa có mô tả chi tiết.",
            "issueDetail.attachments": "Ảnh đính kèm",
            "issueDetail.comments": "Bình Luận",
            "issueDetail.addComment": "Thêm bình luận...",
            "issueDetail.saveComment": "Lưu Bình Luận",
            "issueDetail.status": "Trạng Thái",
            "issueDetail.priority": "Mức Ưu Tiên",
            "issueDetail.assignee": "Người Phụ Trách",
            "issueDetail.dueDate": "Hạn Hoàn Thành / Deadline",
            "issueDetail.reporter": "Người Báo Cáo",
            "issueDetail.created": "Ngày tạo:",
            "issueDetail.updated": "Cập nhật:",
            "issueDetail.unassigned": "Chưa phân công",
            "issueDetail.notice.priority": "Chỉ Project Manager mới có quyền thay đổi độ ưu tiên.",
            "issueDetail.notice.assignee": "Chỉ Project Manager mới có quyền thay đổi người phụ trách.",
            "issueDetail.notice.dueDate": "Chỉ Project Manager mới có quyền thay đổi hạn chót.",
            "issueDetail.notice.status": "Chỉ người tạo, Project Manager hoặc người được giao mới có quyền đổi trạng thái.",
            "issueDetail.modal.editTitle": "Chỉnh Sửa Công Việc",
            "issueDetail.modal.saveChanges": "Lưu Thay Đổi",
            "issueDetail.alert.priorityRestricted": "Chỉ Project Manager hoặc Admin mới có quyền thay đổi độ ưu tiên.",
            "issueDetail.alert.assigneeRestricted": "Chỉ Project Manager hoặc Admin mới có quyền thay đổi người phụ trách.",
            "issueDetail.alert.dueDateRestricted": "Chỉ Project Manager hoặc Admin mới có quyền thay đổi hạn chót (deadline).",
            "issueDetail.comment.contentRequired": "Vui lòng nhập nội dung bình luận trước khi gửi.",
            "issueDetail.comment.textRequiredWithImage": "Vui lòng nhập nội dung bình luận (không được để trống nội dung ngay cả khi có đính kèm ảnh).",

            "board.title": "Bảng Kanban Dự Án",
            "board.subtitle": "Quản lý và kéo thả công việc theo tiến độ quy trình.",
            "board.breadcrumbs.projects": "Dự Án",
            "board.membersBtn": "Thành Viên",
            "board.newIssueBtn": "Tạo Công Việc",
            "board.col.todo": "CẦN LÀM",
            "board.col.inProgress": "ĐANG XỬ LÝ",
            "board.col.inReview": "ĐANG ĐÁNH GIÁ",
            "board.col.done": "HOÀN THÀNH",
            "board.modal.membersTitle": "Thành Viên Dự Án",
            "board.modal.membersSubtitle": "Quản lý quyền truy cập và vai trò của người dùng trong dự án này.",
            "board.modal.currentMembers": "Thành Viên Hiện Tại",
            "board.modal.addNewMember": "Thêm Thành Viên Mới",
            "board.modal.userId": "Chọn Người Dùng",
            "board.modal.projectRole": "Vai Trò Trong Dự Án",
            "board.modal.addMemberBtn": "Thêm Vào Dự Án",
            "board.modal.loadingMembers": "Đang tải danh sách thành viên...",
            "board.modal.noMembers": "Chưa có thành viên nào được thêm vào dự án.",

            "status.todo": "Cần Làm",
            "status.inProgress": "Đang Xử Lý",
            "status.inReview": "Đang Đánh Giá",
            "status.done": "Hoàn Thành",

            "priority.high": "Cao",
            "priority.medium": "Trung Bình",
            "priority.low": "Thấp",
            "priority.critical": "Khẩn Cấp",

            "users.title": "Quản Lý Người Dùng",
            "users.subtitle": "Quản lý tài khoản người dùng, phân quyền hệ thống và trạng thái hoạt động.",
            "users.refresh": "Làm Mới",
            "users.filter.role": "Vai Trò:",
            "users.filter.allRoles": "Tất Cả Vai Trò",
            "users.filter.status": "Trạng Thái:",
            "users.filter.allStatuses": "Tất Cả Trạng Thái",
            "users.filter.active": "Đang Hoạt Động",
            "users.filter.locked": "Đã Khóa",
            "users.filter.unverified": "Chưa Xác Thực",
            "users.searchPlaceholder": "Tìm kiếm theo tên hoặc email...",
            "users.filter.showing": "Hiển thị",
            "users.filter.usersCount": "người dùng",
            "users.table.user": "Người Dùng",
            "users.table.role": "Vai Trò",
            "users.table.status": "Trạng Thái",
            "users.table.joinedDate": "Ngày Tham Gia",
            "users.table.actions": "Hành Động",
            "users.table.loading": "Đang tải danh sách người dùng...",
            "users.table.empty": "Không tìm thấy người dùng nào phù hợp.",
            "users.table.you": "Bạn",
            "users.status.active": "HOẠT ĐỘNG",
            "users.status.locked": "ĐÃ KHÓA",
            "users.actions.changeRole": "Đổi Vai Trò",
            "users.actions.lock": "Khóa",
            "users.actions.unlock": "Mở Khóa",
            "users.action.changeRole": "Đổi Vai Trò",
            "users.action.cannotSelfLock": "Không thể tự khóa tài khoản của chính mình",
            "users.action.cannotSelfDelete": "Không thể tự xóa tài khoản của chính mình",
            "users.action.lockAccount": "Khóa Tài Khoản",
            "users.action.unlockAccount": "Mở Khóa Tài Khoản",
            "users.action.deleteUser": "Xóa Người Dùng",
            "users.modal.changeRoleTitle": "Đổi Vai Trò Hệ Thống",
            "users.modal.selectRole": "Chọn Vai Trò",
            "users.modal.cancel": "Hủy",
            "users.modal.save": "Lưu Thay Đổi",

            "settings.title": "Cài Đặt Tài Khoản",
            "settings.subtitle": "Quản lý thông tin hồ sơ cá nhân và bảo mật tài khoản.",
            "settings.profile.title": "Thông Tin Cá Nhân",
            "settings.profile.active": "Hoạt Động",
            "settings.profile.userId": "ID Người Dùng:",
            "settings.profile.joined": "Ngày Tham Gia:",
            "settings.avatar.change": "Thay avatar",
            "settings.avatar.delete": "Xóa avatar",
            "settings.name.change": "Đổi Tên",
            "settings.name.title": "Đổi Tên Người Dùng",
            "settings.name.subtitle": "Cập nhật họ và tên hiển thị trong toàn bộ hệ thống.",
            "settings.name.inputLabel": "Họ và tên mới",
            "settings.name.placeholder": "Nhập họ và tên...",
            "settings.name.saveBtn": "Lưu Thay Đổi",
            "settings.name.cancel": "Hủy",
            "settings.name.success": "Đổi tên người dùng thành công!",
            "settings.security.title": "Đổi Mật Khẩu",
            "settings.security.subtitle": "Đảm bảo tài khoản của bạn sử dụng mật khẩu mạnh và an toàn.",
            "settings.security.oldPassword": "Mật Khẩu Hiện Tại",
            "settings.security.newPassword": "Mật Khẩu Mới",
            "settings.security.minLengthNotice": "Mật khẩu phải có độ dài tối thiểu 6 ký tự.",
            "settings.security.confirmPassword": "Xác Nhận Mật Khẩu Mới",
            "settings.security.saveBtn": "Cập Nhật Mật Khẩu",
            "settings.session.title": "Phiên Đăng Nhập & Bảo Mật",
            "settings.session.subtitle": "Đăng xuất khỏi phiên làm việc hiện tại trên trình duyệt này.",
            "settings.session.signOut": "Đăng Xuất",
            "settings.session.signOutDesc": "Xóa phiên đăng nhập và thông tin tài khoản được lưu trên thiết bị",
            "settings.alert.mismatch": "Mật khẩu mới và mật khẩu xác nhận không khớp nhau!",
            "settings.alert.success": "Đổi mật khẩu thành công!",
            "settings.alert.fail": "Đổi mật khẩu không thành công. Vui lòng kiểm tra lại mật khẩu hiện tại.",

            "pagination.showing": "Hiển thị {start}–{end} trong tổng {total} bản ghi",
            "pagination.showingEmpty": "Hiển thị 0 trong tổng 0 bản ghi",
            "pagination.perPage": "trang"
        }
    },
    translations: {},

    async init() {
        const lang = this.currentLang || 'en';
        // Khởi tạo trước bằng từ điển nhúng để giao diện hiển thị ngay lập tức
        this.translations = { ...(this.dictionaries[lang] || this.dictionaries.en) };

        try {
            const res = await fetch(`lang/${lang}.json`);
            if (res.ok) {
                const fetched = await res.json();
                this.translations = { ...this.translations, ...fetched };
            }
        } catch (e) {
            // Dùng từ điển fallback
        }

        document.documentElement.setAttribute('lang', lang);
        this.applyTranslations();
        this.updateActiveSelectors();
    },

    async setLanguage(lang) {
        if (!lang) return;
        this.currentLang = lang;
        localStorage.setItem('app_lang', lang);
        document.documentElement.setAttribute('lang', lang);
        await this.init();

        // Kích hoạt lại các callback render nếu trang có
        if (typeof onLanguageChanged === 'function') {
            onLanguageChanged(lang);
        }
    },

    t(key, params = {}) {
        let text = (this.translations && this.translations[key])
            ? this.translations[key]
            : ((this.dictionaries[this.currentLang] && this.dictionaries[this.currentLang][key])
                ? this.dictionaries[this.currentLang][key]
                : (this.dictionaries.en && this.dictionaries.en[key] ? this.dictionaries.en[key] : key));
        if (params && typeof params === 'object') {
            for (const [k, v] of Object.entries(params)) {
                text = text.replace(new RegExp(`\\{${k}\\}`, 'g'), v);
            }
        }
        return text;
    },

    getStatusName(status) {
        if (!status) return '';
        switch (status.toUpperCase()) {
            case 'TODO': return this.t('status.todo');
            case 'IN_PROGRESS': return this.t('status.inProgress');
            case 'REVIEW':
            case 'IN_REVIEW': return this.t('status.inReview');
            case 'DONE': return this.t('status.done');
            default: return status;
        }
    },

    getPriorityName(priority) {
        if (!priority) return '';
        switch (priority.toUpperCase()) {
            case 'HIGH': return this.t('priority.high');
            case 'MEDIUM': return this.t('priority.medium');
            case 'LOW': return this.t('priority.low');
            case 'CRITICAL': return this.t('priority.critical');
            default: return priority;
        }
    },

    applyTranslations() {
        const elements = document.querySelectorAll('[data-i18n]');
        elements.forEach(el => {
            const key = el.getAttribute('data-i18n');
            if (!key) return;
            const translation = this.t(key);
            
            if (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA') {
                el.placeholder = translation;
            } else {
                el.textContent = translation;
            }
        });
    },

    applyI18n() {
        this.applyTranslations();
    },

    updateActiveSelectors() {
        const selects = document.querySelectorAll('.lang-select');
        selects.forEach(select => {
            select.value = this.currentLang;
        });

        const label = document.getElementById('currentLangLabel');
        if (label) {
            label.textContent = this.currentLang.toUpperCase();
        }
    }
};

// Auto Initialize
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => I18n.init());
} else {
    I18n.init();
}
