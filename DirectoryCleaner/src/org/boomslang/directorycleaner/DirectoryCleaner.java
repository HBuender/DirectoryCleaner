package org.boomslang.directorycleaner;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import com.eclipsesource.json.Json;

/**
 * First requests all content of the repository. Afterwards, delete commands are generated and written to a bash script.
 * @author buender
 *
 */
public class DirectoryCleaner {

	private final String USER_AGENT = "Mozilla/5.0";

	private static final int BT_USER = 0;
	private static final int BT_APIKEY = 1;
	private static final int BT_SUBJECT = 2;
	private static final int BT_REPO = 3;
	private static final int BT_PACKAGE = 4;

	public static void main(String[] args) {
		DirectoryCleaner cleaner = new DirectoryCleaner();

		try {
			//Get repository content
			System.out.println("Getting bintray content of: "
					+ args[BT_SUBJECT] + "/" + args[BT_REPO] + "/"
					+ args[BT_PACKAGE]);
			String response = cleaner.getRepositoryContent(args[BT_SUBJECT], args[BT_REPO],
					args[BT_PACKAGE]);
			//Parse the response
			System.out.println("Parsing response:\n" + response);
			List<String> paths = cleaner.parsePaths(response);
			System.out.println("Deleting " + paths.size()
					+ " objects in repository");
			//Create file to delete all artifacts in given repository
			cleaner.writeDeleteFile(cleaner.deletePath(paths, args));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Returns the raw JSON response of the bintray api call for getting the content of a repository.
	 * @param subject
	 * @param repository
	 * @param bintrayPackage
	 * @return
	 * @throws Exception
	 */
	private String getRepositoryContent(String subject, String repository,
			String bintrayPackage) throws Exception {

		String url = "https://api.bintray.com/packages/" + subject + "/"
				+ repository + "/" + bintrayPackage + "/files";
		URL obj = new URL(url);
		HttpURLConnection con = (HttpURLConnection) obj.openConnection();
		// optional default is GET
		con.setRequestMethod("GET");
		// add request header
		con.setRequestProperty("User-Agent", USER_AGENT);

		int responseCode = con.getResponseCode();
		System.out.println("\nSending 'GET' request to URL : " + url);
		System.out.println("Response Code : " + responseCode);

		BufferedReader in = new BufferedReader(new InputStreamReader(
				con.getInputStream()));
		String inputLine;
		StringBuffer response = new StringBuffer();

		while ((inputLine = in.readLine()) != null) {
			response.append(inputLine);
		}
		in.close();

		return response.toString();
	}

	/**
	 * Parses the raw JSON response and return a List of paths to deleteable artifacts in the bintray repository.
	 * @param response
	 * @return
	 */
	private List<String> parsePaths(String response) {
		List<String> paths = new ArrayList<String>();
		if (!("[]".equals(response) || response == null)) {
			String[] rawJsonObjects = response.toString()
					.substring(1, response.toString().length() - 1).split("},");
			for (String rawJsonObject : rawJsonObjects) {
				if (!rawJsonObject.endsWith("}")) {
					rawJsonObject = rawJsonObject + "}";
				}
				paths.add(Json.parse(rawJsonObject).asObject().get("path")
						.toString());
			}
		}
		return paths;
	}

	/**
	 * Creates the delete commands to be entered in a file
	 * @param paths
	 * @param args
	 */
	private String deletePath(List<String> paths, String args[]) {
		
		String rawCommand = "curl -X DELETE -u " + args[BT_USER] + ":"
				+ args[BT_APIKEY] + " \"https://api.bintray.com/content/"
				+ args[BT_SUBJECT] + "/" + args[BT_REPO] + "/";
		StringBuilder builder=new StringBuilder();
		builder.append("#!/bin/bash\n");
		builder.append("function main() {\n");
		builder.append("remove_p2_data\n");
		builder.append("}\n");
		builder.append("function remove_p2_data() {\n");
		for (String path : paths) {
			ExecuteShellCommand com = new ExecuteShellCommand();
			//System.out.println("Deleting path:" + path);
			builder.append("echo \"Removing "+path.replace("\"","")+"...\"\n");
			builder.append(rawCommand + path.replace("\"","") + "\"\n");
			System.out.println(com.executeCommand(rawCommand + path.replace("\"","") + "\""));
		}
		builder.append("}\n");
		builder.append("main \"$@\"");
		return builder.toString();
	}
	/**
	 * Writes the delete commands to a bash script that than later is executed.
	 * @param commandScript
	 */
	private void writeDeleteFile(String commandScript){
		try {
			FileWriter fw = new FileWriter(new File("deleteExistingP2Content.sh"));
			fw.append(commandScript);
			fw.flush();
			fw.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}