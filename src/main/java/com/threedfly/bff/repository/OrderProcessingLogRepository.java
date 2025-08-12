package com.threedfly.bff.repository;

import com.threedfly.bff.entity.OrderProcessingLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OrderProcessingLogRepository extends JpaRepository<OrderProcessingLog, Long> {
    
    List<OrderProcessingLog> findByCustomerId(Long customerId);
    
    List<OrderProcessingLog> findByStatus(String status);
    
    @Query("SELECT opl FROM OrderProcessingLog opl WHERE opl.startedAt BETWEEN :startDate AND :endDate")
    List<OrderProcessingLog> findByDateRange(@Param("startDate") LocalDateTime startDate, 
                                           @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT opl FROM OrderProcessingLog opl WHERE opl.customerId = :customerId AND opl.status = :status")
    List<OrderProcessingLog> findByCustomerIdAndStatus(@Param("customerId") Long customerId, 
                                                      @Param("status") String status);
}
