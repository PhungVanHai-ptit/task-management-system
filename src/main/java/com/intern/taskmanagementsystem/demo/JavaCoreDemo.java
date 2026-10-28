package com.intern.taskmanagementsystem.demo;

import com.intern.taskmanagementsystem.enums.SystemRole;

/**
 * Demo kiến thức Java Core: String Pool và pass-by-value.
 *
 * Đây là class minh hoạ — không phải business logic.
 * Dùng để giải thích trong buổi review với mentor.
 */
public class JavaCoreDemo {

    // ================================================================
    // 1. STRING POOL DEMO
    // ================================================================

    /**
     * Demo: string-pool
     *
     * Java duy trì một String Pool (heap constant pool) để tái sử dụng String literal.
     *
     *  - String literal "abc" → JVM kiểm tra pool, dùng lại nếu đã có → cùng reference
     *  - new String("abc")  → luôn tạo object MỚI trên heap, KHÔNG vào pool
     *  - s.intern()         → đưa string vào pool hoặc trả về reference đã có trong pool
     *
     * Hệ quả thực tế trong project:
     *  → Không dùng == để so sánh String trong business logic
     *  → Dùng .equals() hoặc Objects.equals() khi so sánh nội dung
     *  → Spring Security, Hibernate đều dùng .equals() nội bộ
     */
    public static void stringPoolDemo() {
        // Literal → cùng reference trong pool
        String s1 = "mini-jira";
        String s2 = "mini-jira";
        System.out.println("s1 == s2 (literal):     " + (s1 == s2));        // true
        System.out.println("s1.equals(s2):          " + s1.equals(s2));    // true

        // new String() → object mới, NGOÀI pool
        String s3 = new String("mini-jira");
        System.out.println("s1 == s3 (new):         " + (s1 == s3));       // false ← khác reference!
        System.out.println("s1.equals(s3):          " + s1.equals(s3));    // true  ← cùng nội dung

        // intern() → đưa s3 vào pool, nhận về reference trong pool
        String s4 = s3.intern();
        System.out.println("s1 == s4 (interned):    " + (s1 == s4));       // true ← cùng pool reference

        // === CẢNH BÁO THỰC TẾ ===
        // Trong IssueService, so sánh email/role phải dùng .equals():
        String roleFromDB = new String(SystemRole.ADMIN.name());   // giả lập lấy từ DB
        String roleExpected = SystemRole.ADMIN.name();

        // Sai: roleFromDB == roleExpected  → có thể false!
        // Đúng:
        boolean isAdmin = roleExpected.equals(roleFromDB);  // true
        System.out.println("Role check (equals): " + isAdmin);
    }

    // ================================================================
    // 2. PASSING IN JAVA DEMO (pass-by-value)
    // ================================================================

    /**
     * Demo: passing-in-java
     *
     * Java LUÔN pass-by-value:
     *  - Với primitive (int, long...): copy giá trị → thay đổi trong method không ảnh hưởng ngoài
     *  - Với Object reference: copy địa chỉ (reference) → thay đổi STATE của object ảnh hưởng ngoài,
     *    nhưng reassign reference trong method KHÔNG ảnh hưởng ngoài
     *
     * Ứng dụng trong project:
     *  → Khi Service gọi entity.setStatus(newStatus) → thay đổi state object thật
     *  → Khi method reassign (entity = new Issue(...)) → object ngoài KHÔNG đổi
     */
    public static void passByValueDemo() {
        // --- Primitive: copy giá trị ---
        int count = 10;
        incrementPrimitive(count);
        System.out.println("count after incrementPrimitive: " + count); // vẫn 10

        // --- Object: copy reference ---
        StringBuilder sb = new StringBuilder("PROJ-");
        appendNumber(sb, 42);
        System.out.println("sb after appendNumber: " + sb); // "PROJ-42" ← thay đổi state thật

        // --- Reassign trong method: không ảnh hưởng ngoài ---
        String issueKey = "PROJ-1";
        reassignString(issueKey);
        System.out.println("issueKey after reassign: " + issueKey); // vẫn "PROJ-1"
    }

    private static void incrementPrimitive(int n) {
        n = n + 100; // chỉ thay đổi bản copy cục bộ
    }

    private static void appendNumber(StringBuilder sb, int n) {
        sb.append(n); // thay đổi state của object thật qua reference copy
    }

    private static void reassignString(String s) {
        s = "PROJ-999"; // reassign reference cục bộ — ngoài không bị ảnh hưởng
    }

    // ================================================================
    // Main — chạy tất cả demo
    // ================================================================
    public static void main(String[] args) {
        System.out.println("====== STRING POOL DEMO ======");
        stringPoolDemo();

        System.out.println("\n====== PASSING IN JAVA DEMO ======");
        passByValueDemo();
    }
}
