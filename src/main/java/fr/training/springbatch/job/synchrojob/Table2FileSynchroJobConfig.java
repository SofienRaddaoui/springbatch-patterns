package fr.training.springbatch.job.synchrojob;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

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
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.io.FileSystemResource;

import fr.training.springbatch.app.dto.Customer;
import fr.training.springbatch.app.dto.Transaction;
import fr.training.springbatch.app.job.AbstractJobConfiguration;
import fr.training.springbatch.job.synchrojob.component.CustomerAccumulator;
import fr.training.springbatch.job.synchrojob.component.MasterDetailReader;
import fr.training.springbatch.job.synchrojob.component.TransactionAccumulator;
import fr.training.springbatch.tools.synchro.ItemAccumulator;

/**
 * Using {@link ItemAccumulator} & {@link MasterDetailReader} to "synchronize" 1
 * table and 1 flat file who share the same "customer number" key.
 * <ul>
 * <li>one master table : customer</li>
 * <li>one detail file : transaction csv file</li>
 * </ul>
 *
 * Datas from the detail file (transaction) are stored in the item (customer)
 * returned by the "Master" reader.
 *
 * @author Desprez
 */
public class Table2FileSynchroJobConfig extends AbstractJobConfiguration {

	private static final Logger logger = LoggerFactory.getLogger(Table2FileSynchroJobConfig.class);

	@Value("${application.table2filesynchro-step.chunksize:10}")
	private int chunkSize;

	@Autowired
	private DataSource dataSource;

	/**
	 * @param table2FileSynchroStep the injected Step bean
	 * @return the job bean
	 */
	@Bean
	public Job table2FileSynchroJob(final Step table2FileSynchroStep /* injected by Spring */) {
		return jobBuilderFactory.get("table2filesynchro-job") //
				.incrementer(new RunIdIncrementer()) // job can be launched as many times as desired
				.validator(new DefaultJobParametersValidator(new String[] { "transaction-file", "output-file" },
						new String[] {})) //
				.start(table2FileSynchroStep) //
				.listener(reportListener()) //
				.build();
	}

	/**
	 * @param masterDetailReader the injected {@link MasterDetailReader} bean
	 * @param customerWriter     the injected {@link FlatFileItemWriter} bean
	 * @return a Step bean
	 */
	@Bean
	public Step table2FileSynchroStep(final MasterDetailReader masterDetailReader,
			final ItemWriter<Customer> customerWriter /* injected by Spring */) {

		return stepBuilderFactory.get("table2filesynchro-step") //
				.<Customer, Customer>chunk(chunkSize) //
				.reader(masterDetailReader) //
				.processor(processor()) //
				.writer(customerWriter) //
				.listener(reportListener()) //
				.build();
	}

	/**
	 * Delegate pattern reader
	 *
	 * @param customerReader    the injected Customer {@link ItemReader} bean
	 * @param transactionReader the injected Transaction {@link ItemReader} bean
	 * @return a {@link MasterDetailReader} bean
	 */
	@Bean(destroyMethod = "")
	public MasterDetailReader masterDetailReader(final ItemReader<Customer> customerReader,
			final ItemReader<Transaction> transactionReader) {

		final MasterDetailReader masterDetailReader = new MasterDetailReader();
		masterDetailReader.setMasterAccumulator(new CustomerAccumulator(customerReader));
		masterDetailReader.setDetailAccumulator(new TransactionAccumulator(transactionReader));

		return masterDetailReader;
	}

	/**
	 * @return a {@link JdbcCursorItemReader} bean
	 */
	@Bean
	public JdbcCursorItemReader<Customer> customerReader() {

		return new JdbcCursorItemReaderBuilder<Customer>() //
				.dataSource(dataSource) //
				.name("customerReader") //
				.sql("SELECT * FROM CUSTOMER ORDER BY NUMBER") //
				.rowMapper((rs, rowNum) -> {
					final Customer customer = new Customer();
					customer.setNumber(rs.getString("NUMBER"));
					customer.setAddress(rs.getString("ADDRESS"));
					customer.setCity(rs.getString("CITY"));
					customer.setFirstName(rs.getString("FIRST_NAME"));
					customer.setLastName(rs.getString("LAST_NAME"));
					customer.setPostCode(rs.getString("POST_CODE"));
					customer.setState(rs.getString("STATE"));
					return customer;
				}).build();
	}

	/**
	 * @param transactionFile the injected transaction file job parameter
	 * @return a {@link FlatFileItemReader} bean
	 */
	@StepScope // Mandatory for using jobParameters
	@Bean
	public FlatFileItemReader<Transaction> transactionReader(
			@Value("#{jobParameters['transaction-file']}") final String transactionFile) {

		return new FlatFileItemReaderBuilder<Transaction>() //
				.name("transactionReader") //
				.resource(new FileSystemResource(transactionFile)) //
				.delimited() //
				.delimiter(";") //
				.names(new String[] { "customerNumber", "number", "transactionDate", "amount" }) //
				.linesToSkip(1) //
				.fieldSetMapper(new BeanWrapperFieldSetMapper<Transaction>() {
					{
						setTargetType(Transaction.class);
						setConversionService(createConversionService());
					}
				}).build();
	}

	/**
	 * Processor that sum customer's transactions to compute his balance.
	 *
	 * @return the processor
	 */
	private ItemProcessor<Customer, Customer> processor() {
		return new ItemProcessor<Customer, Customer>() {
			@Override
			public Customer process(final Customer customer) {
				final double sum = customer.getTransactions().stream().mapToDouble(x -> x.getAmount()).sum();
				customer.setBalance(new BigDecimal(sum).setScale(2, RoundingMode.HALF_UP).doubleValue());
				logger.debug(customer.toString());
				return customer;
			}
		};
	}

	/**
	 * @param outputFile the injected output file job parameter
	 * @return a {@link FlatFileItemWriter} bean
	 */
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

	/**
	 * Converter to parse local date
	 */
	public ConversionService createConversionService() {
		final DefaultConversionService conversionService = new DefaultConversionService();
		DefaultConversionService.addDefaultConverters(conversionService);
		conversionService.addConverter(new Converter<String, LocalDate>() {
			@Override
			public LocalDate convert(final String text) {
				final DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;
				return LocalDate.parse(text, formatter);
			}
		});
		return conversionService;
	}

}
