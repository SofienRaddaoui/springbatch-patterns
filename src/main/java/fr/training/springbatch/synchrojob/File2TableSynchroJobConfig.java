package fr.training.springbatch.synchrojob;

import java.math.BigDecimal;
import java.math.RoundingMode;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.DefaultJobParametersValidator;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.builder.FlatFileItemWriterBuilder;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;

import fr.training.springbatch.app.dto.Customer;
import fr.training.springbatch.app.dto.Transaction;
import fr.training.springbatch.common.AbstractJobConfiguration;
import fr.training.springbatch.synchrojob.component.CustomerAccumulator;
import fr.training.springbatch.synchrojob.component.MasterDetailReader;
import fr.training.springbatch.synchrojob.component.TransactionAccumulator;

/**
 * Using ItemAccumulator & MergeDataReader to "synchronize" 1 File and 1 table
 * who share the same key :
 * <ul>
 * <li>one master file</li>
 * <li>one detail table</li>
 * </ul>
 *
 * Datas from the detail table (amounts) are stored in the result (master) of
 * the reader.
 *
 * @author Desprez
 */
@Configuration
public class File2TableSynchroJobConfig extends AbstractJobConfiguration {

	private static final Logger logger = LoggerFactory.getLogger(File2TableSynchroJobConfig.class);

	@Autowired
	private DataSource dataSource;

	@Bean
	public Job file2DBSynchroJob(final Step file2FileSynchroStep /* injected by Spring */) {
		return jobBuilderFactory.get("file2tablesynchro-job") //
				.incrementer(new RunIdIncrementer()) // job can be launched as many times as desired
				.validator(new DefaultJobParametersValidator(new String[] { "customer-file", "output-file" },
						new String[] {})) //
				.start(file2FileSynchroStep) //
				.listener(reportListener()) //
				.build();
	}

	@Bean
	public Step file2DBSynchroStep(final MasterDetailReader masterDetailReader,
			final ItemWriter<? super Customer> customerWriter /* injected by Spring */) {

		return stepBuilderFactory.get("file2tablesynchro-step").<Customer, Customer>chunk(10) //
				.reader(masterDetailReader) //
				.processor(processor()) //
				.writer(customerWriter) //
				.listener(reportListener()) //
				.build();
	}

	// Delegate pattern reader
	@Bean(destroyMethod="")
	public MasterDetailReader masterDetailReader(final ItemReader<Customer> customerReader,
			final ItemReader<Transaction> transactionReader) {

		final MasterDetailReader masterDetailReader = new MasterDetailReader();
		masterDetailReader.setMasterAccumulator(new CustomerAccumulator(customerReader));
		masterDetailReader.setDetailAccumulator(new TransactionAccumulator(transactionReader));

		return masterDetailReader;
	}

	@StepScope // Mandatory for using jobParameters
	@Bean
	public FlatFileItemReader<Customer> customerReader(
			@Value("#{jobParameters['customer-file']}") final String customerFile /* injected by Spring */) {

		return new FlatFileItemReaderBuilder<Customer>() //
				.name("customerReader") //
				.resource(new FileSystemResource(customerFile)) //
				.delimited() //
				.delimiter(";") //
				.names(new String[] { "number", "firstName", "lastName", "address", "city", "state", "postCode" }) //
				.linesToSkip(1) //
				.fieldSetMapper(new BeanWrapperFieldSetMapper<Customer>() {
					{
						setTargetType(Customer.class);
					}
				}).build();
	}

	@Bean
	public JdbcCursorItemReader<Transaction> transactionReader() {

		return new JdbcCursorItemReaderBuilder<Transaction>() //
				.dataSource(dataSource) //
				.name("transactionReader") //
				.sql("SELECT * FROM TRANSACTION ORDER BY CUSTOMER_NUMBER") //
				.rowMapper((rs, rowNum) -> {
					final Transaction transaction = new Transaction();
					transaction.setCustomerNumber(rs.getString("CUSTOMER_NUMBER"));
					transaction.setNumber(rs.getString("NUMBER"));
					transaction.setTransactionDate(rs.getDate("TRANSACTION_DATE").toLocalDate());
					transaction.setAmount(rs.getDouble("AMOUNT"));
					return transaction;
				}).build();
	}

	private ItemProcessor<Customer, Customer> processor() {
		return new ItemProcessor<Customer, Customer>() {
			@Override
			public Customer process(final Customer customer) {
				final double sum = customer.getTransactions().stream().mapToDouble(x -> x.getAmount()).sum();
				customer.setBalance(new BigDecimal(sum).setScale(2, RoundingMode.HALF_UP).doubleValue());
				logger.info(customer.toString());
				return customer;
			}
		};
	}

	@StepScope // Mandatory for using jobParameters
	@Bean
	public FlatFileItemWriter<Customer> customerWriter(
			@Value("#{jobParameters['output-file']}") final String outputFile) {

		return new FlatFileItemWriterBuilder<Customer>().name("customerWriter")
				.resource(new FileSystemResource(outputFile)) //
				.delimited() //
				.delimiter(";") //
				.names(new String[] { "number", "firstName", "lastName", "address", "city", "state", "postCode",
				"balance" })
				.build();

	}

}
