package com.example.pointmanagement.job.reservation;

import com.example.pointmanagement.job.reader.ReverseJpaPagingItemReader;
import com.example.pointmanagement.job.reader.ReverseJpaPagingItemReaderBuilder;
import com.example.pointmanagement.point.Point;
import com.example.pointmanagement.point.PointRepository;
import com.example.pointmanagement.point.reservation.PointReservation;
import com.example.pointmanagement.point.reservation.PointReservationRepository;
import com.example.pointmanagement.point.wallet.PointWallet;
import com.example.pointmanagement.point.wallet.PointWalletRepository;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.partition.support.TaskExecutorPartitionHandler;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.domain.Sort;
import org.springframework.data.util.Pair;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;

@Configuration
public class ExecutePointReservationStepConfiguration {

    @Bean
    @JobScope
    public Step executePointReservationMasterStep(
            JobRepository jobRepository,
            TaskExecutorPartitionHandler partitionHandler,
            PointReservationRepository pointReservationRepository,
            @Value("#{T(java.time.LocalDate).parse(jobParameters[today])}")
            LocalDate today
    ) {
        return new StepBuilder("executePointReservationMasterStep", jobRepository)
                .partitioner(
                        "executePointReservationStep",
                        new ExecutePointReservationStepPartitioner(pointReservationRepository, today)
                )
                .partitionHandler(partitionHandler)
                .build();
    }

    @Bean
    public TaskExecutorPartitionHandler partitionHandler( // 로컬에서 쓰레드로 실행
            Step executePointReservationStep,
            TaskExecutor taskExecutor
    ) {
        TaskExecutorPartitionHandler partitionHandler = new TaskExecutorPartitionHandler();
        partitionHandler.setStep(executePointReservationStep);
        partitionHandler.setGridSize(8);
        partitionHandler.setTaskExecutor(taskExecutor);
        return partitionHandler;
    }

    @Bean
    @JobScope
    public Step executePointReservationStep(
            JobRepository jobRepository,
            PlatformTransactionManager platformTransactionManager,
            ReverseJpaPagingItemReader<PointReservation> executePointReservationItemReader,
            ItemProcessor<PointReservation, Pair<PointReservation, Point>> executePointReservationItemProcessor,
            ItemWriter<Pair<PointReservation, Point>> executePointReservationItemWriter

    ) {
        return new StepBuilder("executePointReservationStep", jobRepository)
                .allowStartIfComplete(true)
                .<PointReservation, Pair<PointReservation, Point>>chunk(1000, platformTransactionManager)
                .reader(executePointReservationItemReader)
                .processor(executePointReservationItemProcessor)
                .writer(executePointReservationItemWriter)
                .build();
    }

//    @Bean
//    @StepScope
//    public JpaPagingItemReader<PointReservation> executePointReservationItemReader(
//            EntityManagerFactory entityManagerFactory,
//            @Value("#{T(java.time.LocalDate).parse(jobParameters[today])}")
//            LocalDate today
//
//    ) {
//        return new JpaPagingItemReaderBuilder<PointReservation>()
//                .name("executePointReservationItemReader")
//                .entityManagerFactory(entityManagerFactory)
//                .queryString("select pr from PointReservation pr where pr.earnedDate = :today and pr.executed = false")
//                .parameterValues(Map.of("today", today))
//                .pageSize(1000)
//                .build();
//    }

    @Bean
    @StepScope
    public ReverseJpaPagingItemReader<PointReservation> executePointReservationItemReader(
            PointReservationRepository pointReservationRepository,
            @Value("#{T(java.time.LocalDate).parse(jobParameters[today])}") LocalDate today,
            @Value("#{stepExecutionContext[minId]}") Long minId,
            @Value("#{stepExecutionContext[maxId]}") Long maxId
    ) {
        return new ReverseJpaPagingItemReaderBuilder<PointReservation>()
                .name("messageExpireSoonPointItemReader")
                .query(
                        pageable -> pointReservationRepository.findPointReservationToExecute(today, minId, maxId, pageable)
                )
                .pageSize(1000)
                .sort(Sort.by(Sort.Direction.ASC, "id"))
                .build();
    }

    @Bean
    @StepScope
    public ItemProcessor<PointReservation, Pair<PointReservation, Point>> executePointReservationItemProcessor() {
        return reservation -> {
            reservation.setExecuted(true);
            Point earnedPoint = new Point(
                    reservation.getPointWallet(),
                    reservation.getAmount(),
                    reservation.getEarnedDate(),
                    reservation.getExpiredDate()
            );
            PointWallet wallet = earnedPoint.getPointWallet();
            wallet.setAmount(wallet.getAmount().add(earnedPoint.getAmount()));
            return Pair.of(reservation, earnedPoint);
        };
    }

    @Bean
    @StepScope
    public ItemWriter<Pair<PointReservation, Point>> executePointReservationItemWriter(
            PointReservationRepository pointReservationRepository,
            PointRepository pointRepository,
            PointWalletRepository pointWalletRepository
    ) {
        return reservationAndPointPairs -> {
            for (Pair<PointReservation, Point> pair : reservationAndPointPairs) {
                PointReservation reservation = pair.getFirst();
                pointReservationRepository.save(reservation);
                Point point = pair.getSecond();
                pointRepository.save(point);
                pointWalletRepository.save(point.getPointWallet());
            }
        };
    }
}
