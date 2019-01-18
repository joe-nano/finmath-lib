package net.finmath.modelling.descriptor.xmlparser;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.xml.parsers.ParserConfigurationException;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.xml.sax.SAXException;

import net.finmath.marketdata.model.AnalyticModel;
import net.finmath.marketdata.model.AnalyticModelInterface;
import net.finmath.marketdata.model.curves.Curve;
import net.finmath.marketdata.model.curves.CurveInterface;
import net.finmath.marketdata.model.curves.DiscountCurveInterface;
import net.finmath.marketdata.model.curves.ForwardCurve;
import net.finmath.marketdata.model.curves.ForwardCurveInterface;
import net.finmath.marketdata.products.Swap;
import net.finmath.marketdata.products.SwapLeg;
import net.finmath.modelling.DescribedProduct;
import net.finmath.modelling.ProductDescriptor;
import net.finmath.modelling.descriptor.InterestRateSwapLegProductDescriptor;
import net.finmath.modelling.descriptor.InterestRateSwapProductDescriptor;
import net.finmath.modelling.productfactory.InterestRateAnalyticProductFactory;
import net.finmath.modelling.productfactory.ModelWithProductFactoryTest;
import net.finmath.time.businessdaycalendar.BusinessdayCalendarExcludingTARGETHolidays;
import net.finmath.time.businessdaycalendar.BusinessdayCalendar;

@RunWith(Parameterized.class)
public class FPMLParserTest {

	@Parameters(name="file={0}}")
	public static Collection<Object[]> generateData()
	{
		/// @TODO Provide a list of test files here
		ArrayList<Object[]> parameters = new ArrayList<>();

		ClassLoader classLoader = FPMLParserTest.class.getClassLoader();
		try {
			parameters.add(new Object[] { new File(classLoader.getResource("fpml/ird-ex01-vanilla-swap.xml").toURI()) });
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
		return parameters;
	}

	private final File file;

	/**
	 * This main method will prompt the user for a test file an run the test with the given file.
	 *
	 * @param args Arguments - not used.
	 * @throws ParserConfigurationException
	 * @throws IOException
	 * @throws SAXException
	 */
	public static void main(String[] args) throws SAXException, IOException, ParserConfigurationException
	{
		JFileChooser jfc = new JFileChooser(System.getProperty("user.home"));
		jfc.setDialogTitle("Choose XML");
		jfc.setFileFilter(new FileNameExtensionFilter("FIPXML (.xml)", "xml"));
		if(jfc.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) {
			System.exit(1);
		}

		(new FPMLParserTest(jfc.getSelectedFile())).testGetSwapProductDescriptor();
	}

	public FPMLParserTest(File file) {
		super();
		this.file = file;
	}

	@Test
	public void testGetSwapProductDescriptor() throws SAXException, IOException, ParserConfigurationException {

		InterestRateSwapProductDescriptor descriptor;
		try {
			FPMLParser parser = new FPMLParser("party1", "discount");
			descriptor = (InterestRateSwapProductDescriptor) parser.getProductDescriptor(file);
		} catch (IllegalArgumentException e) {
			System.out.println("There was a problem with the file: "+e.getMessage());
			//			e.printStackTrace();
			return;
		} catch (FileNotFoundException e) {
			System.out.println("File not found. We will exit gracefully.");
			return;
		}

		InterestRateSwapLegProductDescriptor legReceiver	= (InterestRateSwapLegProductDescriptor) descriptor.getLegReceiver();
		InterestRateSwapLegProductDescriptor legPayer		= (InterestRateSwapLegProductDescriptor) descriptor.getLegPayer();

		System.out.println("Receiver leg:");
		System.out.println(legReceiver.name());
		System.out.println(legReceiver.getForwardCurveName());
		System.out.println(legReceiver.getDiscountCurveName());
		System.out.println(Arrays.toString(legReceiver.getNotionals()));
		System.out.println(Arrays.toString(legReceiver.getSpreads()));
		System.out.println(legReceiver.getLegScheduleDescriptor());

		System.out.println("\n\nPayer leg:");
		System.out.println(legPayer.name());
		System.out.println(legPayer.getForwardCurveName());
		System.out.println(legPayer.getDiscountCurveName());
		System.out.println(Arrays.toString(legPayer.getNotionals()));
		System.out.println(Arrays.toString(legPayer.getSpreads()));
		System.out.println(legPayer.getLegScheduleDescriptor());


		LocalDate referenceDate = LocalDate.of(1995,1,10);
		DiscountCurveInterface discountCurve = ModelWithProductFactoryTest.getDiscountCurve("discount", referenceDate, 0.05);
		ForwardCurveInterface forwardCurve = getForwardCurve("EUR-LIBOR-BBA", referenceDate);
		AnalyticModelInterface model = new AnalyticModel(referenceDate, new CurveInterface[] { discountCurve, forwardCurve });

		InterestRateAnalyticProductFactory productFactory = new InterestRateAnalyticProductFactory(referenceDate);
		DescribedProduct<? extends ProductDescriptor> legReceiverProduct = productFactory.getProductFromDescriptor(legReceiver);
		DescribedProduct<? extends ProductDescriptor> legPayerProduct = productFactory.getProductFromDescriptor(legPayer);

		Swap swap = new Swap((SwapLeg)legReceiverProduct, (SwapLeg)legPayerProduct);

		double value = swap.getValue(0.0, model);
		double valueBenchmark = 1876630.58;

		System.out.println();
		System.out.println("Swap value (on idealized curve): " + value);

		Assert.assertEquals("Benchmark value", valueBenchmark, value,1E-2);
	}

	public static ForwardCurve getForwardCurve(String name, LocalDate referenceDate) {
		return ForwardCurve.createForwardCurveFromForwards(
				name,
				referenceDate,
				"6M",
				new BusinessdayCalendarExcludingTARGETHolidays(),
				BusinessdayCalendar.DateRollConvention.FOLLOWING,
				Curve.InterpolationMethod.LINEAR,
				Curve.ExtrapolationMethod.CONSTANT,
				Curve.InterpolationEntity.VALUE,
				ForwardCurve.InterpolationEntityForward.FORWARD,
				null,
				null,
				new double[] {0.5 , 1.0 , 2.0 , 5.0 , 40.0}	/* fixings of the forward */,
				new double[] {0.05, 0.05, 0.05, 0.05, 0.05}	/* forwards */
				);
	}
}
