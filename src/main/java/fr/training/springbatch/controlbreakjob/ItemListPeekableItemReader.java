package fr.training.springbatch.controlbreakjob;

import java.util.ArrayList;
import java.util.List;

import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemStream;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ParseException;
import org.springframework.batch.item.UnexpectedInputException;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.support.SingleItemPeekableItemReader;

/**
 *
 * @author PASCALDesprez
 *
 * @param <T>
 */
public class ItemListPeekableItemReader<T> implements ItemReader<List<T>>, ItemStream {

	private SingleItemPeekableItemReader<T> delegate;

	private BreakKeyStrategy<T> breakKeyStrategy;

	@Override
	public List<T> read() throws UnexpectedInputException, ParseException, Exception {

		final List<T> records = new ArrayList<T>();

		final T item = delegate.read();

		if (item == null) {
			return null;
		}
		records.add(item);

		while (true) {
			final T possibleSameGroupItem = delegate.peek();
			if (possibleSameGroupItem == null) {
				return records;
			}

			if (breakKeyStrategy.isSameGroup(item, possibleSameGroupItem)) {
				records.add(delegate.read());
			} else {
				return records;
			}
		}
	}

	public void setDelegate(final FlatFileItemReader<T> delegate) {
		this.delegate = new SingleItemPeekableItemReader<T>();
		this.delegate.setDelegate(delegate);
	}

	public void setBreakKeyStrategy(final BreakKeyStrategy<T> breakKeyStrategy) {
		this.breakKeyStrategy = breakKeyStrategy;
	}

	@Override
	public void close() throws ItemStreamException {
		delegate.close();
	}

	@Override
	public void open(final ExecutionContext executionContext) throws ItemStreamException {
		delegate.open(executionContext);
	}

	@Override
	public void update(final ExecutionContext executionContext) throws ItemStreamException {
		delegate.update(executionContext);
	}

}