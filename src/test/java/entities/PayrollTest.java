package entities;

import org.junit.jupiter.api.Test;

import java.sql.Date;
import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.*;

class PayrollTest {

    @Test
    void defaultConstructor() {
        Payroll p = new Payroll();
        assertEquals(0, p.getId());
        assertEquals(0.0, p.getAmount());
        assertNull(p.getStatus());
    }

    @Test
    void parameterizedConstructorSetsPendingAndAmount() {
        Date month = Date.valueOf("2025-06-01");
        Payroll p = new Payroll(10, month, 2025, 3000.0, 500.0, 200.0, 3300.0, 160.0, 20.0);

        assertEquals(10, p.getUserId());
        assertEquals(month, p.getMonth());
        assertEquals(2025, p.getYear());
        assertEquals(3000.0, p.getBaseSalary());
        assertEquals(500.0, p.getBonus());
        assertEquals(200.0, p.getDeductions());
        assertEquals(3300.0, p.getNetSalary());
        assertEquals(3300.0, p.getAmount(), "Amount should equal netSalary");
        assertEquals(160.0, p.getTotalHoursWorked());
        assertEquals(20.0, p.getHourlyRate());
        assertEquals("PENDING", p.getStatus());
    }

    @Test
    void fullConstructor() {
        Date month = Date.valueOf("2025-05-01");
        Timestamp ts = Timestamp.valueOf("2025-05-31 23:59:59");
        Payroll p = new Payroll(1, 10, month, 2025, 3300.0, "PAID",
                3000.0, 500.0, 200.0, 3300.0, 160.0, 20.0, ts);

        assertEquals(1, p.getId());
        assertEquals("PAID", p.getStatus());
        assertEquals(3300.0, p.getAmount());
        assertEquals(ts, p.getGeneratedAt());
    }

    @Test
    void settersAndGetters() {
        Payroll p = new Payroll();
        p.setId(5);
        p.setUserId(20);
        Date m = Date.valueOf("2025-01-01");
        p.setMonth(m);
        p.setYear(2025);
        p.setAmount(4000.0);
        p.setStatus("PAID");
        p.setBaseSalary(3500.0);
        p.setBonus(600.0);
        p.setDeductions(100.0);
        p.setNetSalary(4000.0);
        p.setTotalHoursWorked(176.0);
        p.setHourlyRate(22.5);
        Timestamp ts = new Timestamp(System.currentTimeMillis());
        p.setGeneratedAt(ts);

        assertEquals(5, p.getId());
        assertEquals(20, p.getUserId());
        assertEquals(m, p.getMonth());
        assertEquals(2025, p.getYear());
        assertEquals(4000.0, p.getAmount());
        assertEquals("PAID", p.getStatus());
        assertEquals(3500.0, p.getBaseSalary());
        assertEquals(600.0, p.getBonus());
        assertEquals(100.0, p.getDeductions());
        assertEquals(4000.0, p.getNetSalary());
        assertEquals(176.0, p.getTotalHoursWorked());
        assertEquals(22.5, p.getHourlyRate());
        assertEquals(ts, p.getGeneratedAt());
    }

    @Test
    void toStringContainsFields() {
        Payroll p = new Payroll(10, Date.valueOf("2025-06-01"), 2025,
                3000.0, 500.0, 200.0, 3300.0, 160.0, 20.0);
        String s = p.toString();
        assertTrue(s.contains("userId=10"));
        assertTrue(s.contains("netSalary=3300.0"));
        assertTrue(s.contains("PENDING"));
    }
}
