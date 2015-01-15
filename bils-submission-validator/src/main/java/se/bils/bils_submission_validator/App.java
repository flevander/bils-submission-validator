package se.bils.bils_submission_validator;

import uk.ac.ebi.pride.validator.IValidator;
import uk.ac.ebi.pride.validator.schema.MzMLSchemaValidator;
import uk.ac.ebi.pride.validator.schema.PxXmlSchemaValidatorFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.List;

/**
 * Hello world!
 */
public class App
{
	public static void main(String[] args)
	{
		if (args.length < 1)
		{
			System.err
				.println("Invalid number of arguments. A px file needs to be given as argument");
			System.exit(1);
		}
		try
		{
			InputStream is = new FileInputStream(args[0]);
			BufferedReader reader = new BufferedReader(
				new InputStreamReader(is));
			String line;
			IValidator<File, String> schemaValidator = PxXmlSchemaValidatorFactory
				.getPxXmlSchemaValidator();
			List<String> errors = new ArrayList<String>();
			int filesSubmitted=0;
			int filesValidated=0;
			int filesNotFound=0;
			while ((line = reader.readLine()) != null)
			{
				String[] fields = line.split("\\t");
				if (fields[0].equals("FME") && fields.length > 3)
				{
					try
					{
						filesSubmitted++;
						System.out.println("Validating:" + fields[3]);
						String toValidate = fields[3];
						if (toValidate.startsWith("http"))
						{
							String fileName = toValidate.substring(toValidate
								.lastIndexOf('/') + 1);
							System.out.print("Downloading file...");
							HttpURLConnection connection = getConnection(toValidate);
							InputStream fis = connection.getInputStream();
							ReadableByteChannel rbc = Channels.newChannel(fis);
							FileOutputStream fos = new FileOutputStream(
								fileName);
							fos.getChannel().transferFrom(rbc, 0,
								Long.MAX_VALUE);
							fos.close();
							System.out.println("Downloaded to " + fileName);
							toValidate = fileName;
						}
						File fileToValidate = new File(toValidate);
						List<String> responses = new ArrayList<String>();
						if (fileToValidate.exists())
						{
							if (schemaValidator.support(fileToValidate))
							{
								filesValidated++;
								try
								{
									responses = schemaValidator
										.validate(fileToValidate);
									if (!responses.isEmpty() && (toValidate
										.endsWith(".mzML") || toValidate
										.endsWith(".mzML.gz")) && responses
										.get(0).contains("indexed"))
									{
										System.out
											.println("Validating indexed mzML");
										MzMLSchemaValidator mzmlSchemaValidator = new MzMLSchemaValidator(
											new URI(
												"http://www.psidev.info/files/ms/mzML/xsd/mzML1.1.1_idx.xsd"));
										responses = mzmlSchemaValidator
											.validate(fileToValidate);
									}									
								}
								catch (IllegalStateException e)
								{
									responses.add("Cannot validate file:" + e);
								}
							}
							else
							{
								System.out.println("Validation of file not supported");
							}
							if (!toValidate.equals(fields[3]))
							{
								fileToValidate.delete();
							}
						}
						else
						{
							responses.add("File not found");
							filesNotFound++;
						}
						for (String s : responses)
						{
							if (s != null)
							{
								System.out.println("Result:" + s);
								errors.add(fields[3]+":"+s);
							}
						}
					}
					catch (URISyntaxException e)
					{
						System.out.println("Incorrect URI:" + e);
					}
				}

			}
			reader.close();
			System.out.println("VALIDATION SUMMARY");
			if (errors.size()>0) System.out.println("Detected errors:");
			for (String s:errors)
			{
				System.out.println(s);
			}
			System.out.println("Number of files:"+filesSubmitted);
			System.out.println("Number of validated files:"+filesValidated);
			if (filesNotFound>0) System.out.println("Files not found:"+filesNotFound);
		}
		catch (FileNotFoundException e)
		{
			e.printStackTrace();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}

	}


	/**
	 * Get a HTTP/HTTPS connection and handle redirection. The connection can be
	 * used to download a stream.
	 * 
	 * @param url_str
	 * @return
	 * @throws MalformedURLException, IOException
	 */
	private static HttpURLConnection getConnection(String url_str)
			throws MalformedURLException, IOException
	{
		String basicAuth = "";
		if (url_str.matches(".*:\\/\\/.*:.*@.*"))
		{
			String userAndPw = url_str.substring(url_str.indexOf("://") + 3,
				url_str.indexOf("@"));
			url_str = url_str.replace("userAndPassword" + "@", "");
			basicAuth = "Basic " + javax.xml.bind.DatatypeConverter
				.printBase64Binary(userAndPw.getBytes());
		}
		URL url = new URL(url_str);
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		if (!basicAuth.equals(""))
			connection.setRequestProperty("Authorization", basicAuth);
		connection.setUseCaches(false);
		connection.setInstanceFollowRedirects(true);
		connection.setReadTimeout(120000);
		connection.setConnectTimeout(60000);
		//
		// Check the HTTP response code, and redirect if needed
		//
		int status = connection.getResponseCode();
		boolean redirect = (status == HttpURLConnection.HTTP_MOVED_TEMP) || (status == HttpURLConnection.HTTP_MOVED_PERM);
		if (redirect)
		{
			String url_moved = connection.getHeaderField("location");
			String scheme = url_str.split("://")[0].toLowerCase();
			String scheme_moved = url_moved.split("://")[0].toLowerCase();
			if (scheme != scheme_moved)
			{
				// create a connection to url_moved
				connection.disconnect();
				// System.out.println("# Scheme = " + scheme + " New = " +
				// scheme_moved);
				connection = getConnection(url_moved);
			}
		}
		return connection;
	} // getConnection()
}
