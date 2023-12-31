package com.example.pointmanagement.job.expire.message;

import com.example.pointmanagement.job.validator.TodayJobParameterValidator;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MessageExpiredPointJobConfiguration {
    @Bean
    public Job messageExpiredPointJob(
            JobRepository jobRepository,
            TodayJobParameterValidator validator,
            Step messageExpiredPointStep
    ) {
        return new JobBuilder("messageExpiredPointJob", jobRepository)
                .validator(validator)
                .incrementer(new RunIdIncrementer())
                .start(messageExpiredPointStep)
                .build();
    }
}
