package com.batch.config;


import java.util.Date;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.item.database.BeanPropertyItemSqlParameterSourceProvider;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.item.file.LineMapper;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.batch.item.file.mapping.DefaultLineMapper;
import org.springframework.batch.item.file.transform.BeanWrapperFieldExtractor;
import org.springframework.batch.item.file.transform.DelimitedLineAggregator;
import org.springframework.batch.item.file.transform.DelimitedLineTokenizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.batch.model.UserVO;
import com.batch.processor.UserItemProcessor;





@Configuration
@EnableBatchProcessing
@EnableScheduling
public class BatchConfig {
	
	@Autowired
	private JobBuilderFactory jobBuilderFactory;
	
	@Autowired
	private StepBuilderFactory stepBuilderFactory;
	
	@Autowired
	DataSource dataSource;
	
	@Autowired
	public JobLauncher jobLauncher;
	 
	Logger logger = LoggerFactory.getLogger("Job Started");
	 //cron 표현식 : 매일 오전 3시에 0분~59분까지 매분 실행
	@Scheduled(cron = "0 * 11 * * *")
	public void perform() throws Exception{
		
		logger.info("Job Strarted" + new Date());
		
		JobParameters param = new JobParametersBuilder().addString("JobId", String.valueOf(System.currentTimeMillis())).toJobParameters();
		JobExecution execution = jobLauncher.run(stepJob(), param);
		
		logger.info("stepJob finished with status : " + execution.getStatus());
	}
	
	public class StepResultLister implements StepExecutionListener{

		@Override
		public void beforeStep(StepExecution stepExecution) {
			JdbcTemplate jt = new JdbcTemplate();
			jt.setDataSource(dataSource);
			jt.execute("TRUNCATE TABLE THIRD_BATCH");
		}

		@Override
		public ExitStatus afterStep(StepExecution stepExecution) {
			return null;
		}
	}
	

	@Bean(destroyMethod="")
	public JdbcCursorItemReader<UserVO> reader1(){
	
		 JdbcCursorItemReader<UserVO> cursorItemReader1 = new JdbcCursorItemReader<>();
		 cursorItemReader1.setDataSource(dataSource);
		 cursorItemReader1.setSql("SELECT COUNT(*) AS count FROM FIRST_BATCH");
		 cursorItemReader1.setRowMapper(new CountVORowMapper());	 
		 return cursorItemReader1;
	}
	
	@Bean 
	public UserItemProcessor processor1() {
		return new UserItemProcessor();
	}
	
	@Bean
	public JdbcBatchItemWriter<UserVO> writer1() {

		JdbcBatchItemWriter<UserVO> writer1 = new JdbcBatchItemWriter<>();
		writer1.setDataSource(dataSource);
		writer1.setSql("INSERT INTO COUNT_BATCH(count) VALUES(:count)");
		writer1.setItemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<UserVO>());
		
		writer1.afterPropertiesSet();
		
		return writer1;
		
	}
	
	@Bean
	public Step step1() {
		
		System.out.println("step1():count");
		
		return stepBuilderFactory
				.get("step1")
				
				.<UserVO, UserVO>chunk(1)
				.reader(reader1())
				.processor(processor1())
				.writer(writer1())
				.build();
				
	}
	
	@Bean(destroyMethod="")
	public JdbcCursorItemReader<UserVO> reader2() {
	
		JdbcCursorItemReader<UserVO> cursorItemReader2 = new JdbcCursorItemReader<>();
		cursorItemReader2.setDataSource(dataSource);
		cursorItemReader2.setSql("SELECT * FROM FIRST_BATCH");
		cursorItemReader2.setRowMapper(new UserVORowMapper());
	
		return cursorItemReader2;
	}
	
	@Bean
	public UserItemProcessor processor2() {
		return new UserItemProcessor();
	}
	
	@Bean
	public FlatFileItemWriter<UserVO> writer2(){
		FlatFileItemWriter<UserVO> writer2 = new FlatFileItemWriter<UserVO>();
		writer2.setResource(new FileSystemResource("src/main/resources/users.csv"));
		//fileSystemResource
		DelimitedLineAggregator<UserVO> lineAggregator = new DelimitedLineAggregator<UserVO>();
		lineAggregator.setDelimiter(" , ");
	
		BeanWrapperFieldExtractor<UserVO> fieldExtractor = new BeanWrapperFieldExtractor<UserVO>();
		fieldExtractor.setNames(new String[] {"id", "name", "sys_date"});
		lineAggregator.setFieldExtractor(fieldExtractor);
		
		writer2.setLineAggregator(lineAggregator);
		//CSV파일 한글 설정
		writer2.setEncoding("CP949");
		return writer2;
	}
	
	@Bean
	public Step step2() {
		
		System.out.println("step2():create csvfile");
		
		return stepBuilderFactory
				.get("step2")
				.<UserVO, UserVO>chunk(1)
				.reader(reader2())
				.processor(processor2())
				.writer(writer2())
				.build();
	}

    @Bean
    public FlatFileItemReader<UserVO> reader3() {

        FlatFileItemReader<UserVO> reader3 = new FlatFileItemReader<>();
        reader3.setResource(new FileSystemResource("src/main/resources/users.csv"));
        reader3.setName("CSV-Reader");
        reader3.setEncoding("CP949");
        reader3.setLineMapper(lineMapper());
        return reader3;
    }
    
    @Bean
    public LineMapper<UserVO> lineMapper() {

        DefaultLineMapper<UserVO> defaultLineMapper = new DefaultLineMapper<>();
        DelimitedLineTokenizer lineTokenizer = new DelimitedLineTokenizer();

        lineTokenizer.setDelimiter(",");
        lineTokenizer.setStrict(false);
        lineTokenizer.setNames(new String[] {"id", "name", "sys_date"});

        BeanWrapperFieldSetMapper<UserVO> fieldSetMapper = new BeanWrapperFieldSetMapper<>();
        fieldSetMapper.setTargetType(UserVO.class);

        defaultLineMapper.setLineTokenizer(lineTokenizer);
        defaultLineMapper.setFieldSetMapper(fieldSetMapper);
        return defaultLineMapper;
    }
	@Bean 
	public UserItemProcessor processor3() {
		return new UserItemProcessor();
	}
	
	@Bean
	public JdbcBatchItemWriter<UserVO> writer3(){
		
		JdbcBatchItemWriter<UserVO> writer3 = new JdbcBatchItemWriter<>();
		writer3.setDataSource(dataSource);
		writer3.setItemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<UserVO>());
		writer3.setSql("INSERT INTO THIRD_BATCH (id, name, sys_date) VALUES(:id, :name, :sys_date)");
	
		return writer3;
	}
	
	@Bean
	public Step step3() {
		
		System.out.println("step3():copy from first_batch to third_batch");
		
		return stepBuilderFactory.get("step3")
				.<UserVO, UserVO> chunk(1)
				.reader(reader3())
				.processor(processor3())
				.writer(writer3())
				.listener(new StepResultLister())
				.build();
	}
	

	@Bean
	public Job stepJob() {
		return jobBuilderFactory.get("stepJob")
			.start(step1())
				.on("*")
				.to(step2())
				.on(ExitStatus.FAILED.getExitCode())
				.end()
			.from(step2())
				.next(step3())
				.on("*")
				.end()
			.end()
			.build();			
	}
}
