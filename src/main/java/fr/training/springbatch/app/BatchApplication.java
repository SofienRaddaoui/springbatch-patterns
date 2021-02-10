package fr.training.springbatch.app;

import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.support.ApplicationContextFactory;
import org.springframework.batch.core.configuration.support.GenericApplicationContextFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import fr.training.springbatch.exportjob.SimpleExportJobConfig;
import fr.training.springbatch.importjob.SimpleImportJobConfig;
import fr.training.springbatch.purgejob.PurgeHistoryJob;
import fr.training.springbatch.stagingjob.StagingJobConfig;
import fr.training.springbatch.synchrojob.File2FileSynchroJobConfig;
import fr.training.springbatch.synchrojob.File2TableSynchroJobConfig;
import fr.training.springbatch.synchrojob.GroupingRecordsJobConfig;
import fr.training.springbatch.synchrojob.SQLJoinSynchroJobConfig;
import fr.training.springbatch.synchrojob.Table2FileSynchroJobConfig;

@SpringBootApplication
@EnableBatchProcessing(modular = true)
public class BatchApplication {

	@Value("${spring.batch.job.names:#{null}}")
	private String jobName;

	public static void main(final String[] args) {
		System.exit(SpringApplication.exit(SpringApplication.run(BatchApplication.class, args)));
	}

	@Bean
	public ApplicationContextFactory simpleExportJobContextFactory() {
		return new GenericApplicationContextFactory(SimpleExportJobConfig.class);
	}

	@Bean
	public ApplicationContextFactory simpleImportJobContextFactory() {
		return new GenericApplicationContextFactory(SimpleImportJobConfig.class);
	}

	@Bean
	public ApplicationContextFactory stagingJobContextFactory() {
		return new GenericApplicationContextFactory(StagingJobConfig.class);
	}

	@Bean
	public ApplicationContextFactory table2FileSynchroJobContextFactory() {
		return new GenericApplicationContextFactory(Table2FileSynchroJobConfig.class);
	}

	@Bean
	public ApplicationContextFactory file2TableSynchroJobContextFactory() {
		return new GenericApplicationContextFactory(File2TableSynchroJobConfig.class);
	}

	@Bean
	public ApplicationContextFactory file2FileSynchroJobContextFactory() {
		return new GenericApplicationContextFactory(File2FileSynchroJobConfig.class);
	}

	@Bean
	public ApplicationContextFactory groupingRecordsJobContextFactory() {
		return new GenericApplicationContextFactory(GroupingRecordsJobConfig.class);
	}

	@Bean
	public ApplicationContextFactory sqlJoinSynchroJobContextFactory() {
		return new GenericApplicationContextFactory(SQLJoinSynchroJobConfig.class);
	}

	@Bean
	public ApplicationContextFactory purgeHistoryJobContextFactory() {
		return new GenericApplicationContextFactory(PurgeHistoryJob.class);
	}

}
