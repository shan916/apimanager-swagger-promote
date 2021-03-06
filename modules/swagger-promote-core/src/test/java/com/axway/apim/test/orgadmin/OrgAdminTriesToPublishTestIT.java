package com.axway.apim.test.orgadmin;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.testng.annotations.Optional;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

import com.axway.apim.lib.AppException;
import com.axway.apim.test.ImportTestAction;
import com.consol.citrus.annotations.CitrusResource;
import com.consol.citrus.annotations.CitrusTest;
import com.consol.citrus.context.TestContext;
import com.consol.citrus.dsl.testng.TestNGCitrusTestRunner;
import com.consol.citrus.functions.core.RandomNumberFunction;
import com.consol.citrus.message.MessageType;

@Test
public class OrgAdminTriesToPublishTestIT extends TestNGCitrusTestRunner {

	private ImportTestAction swaggerImport;
	
	@CitrusTest
	@Test @Parameters("context")
	public void run(@Optional @CitrusResource TestContext context) throws IOException, AppException {
		swaggerImport = new ImportTestAction();
		
		description("But OrgAdmins should not being allowed to register published APIs.");
		
		variable("apiNumber", RandomNumberFunction.getRandomNumber(3, true));
		variable("apiPath", "/org-admin-published-${apiNumber}");
		variable("apiName", "OrgAdmin-Published-${apiNumber}");
		variable("ignoreAdminAccount", "true"); // This tests simulate to use only an Org-Admin-Account
		variable("allowOrgAdminsToPublish", "false"); // Disable OrgAdmins to publish APIs

		echo("####### Calling the tool with a Non-Admin-User. #######");
		createVariable(ImportTestAction.API_DEFINITION,  "/com/axway/apim/test/files/basic/petstore.json");
		createVariable(ImportTestAction.API_CONFIG,  "/com/axway/apim/test/files/basic/2_initially_published.json");
		createVariable("expectedReturnCode", "17");
		createVariable("apiManagerUser", "${oadminUsername1}"); // This is an org-admin user
		createVariable("apiManagerPass", "${oadminPassword1}");
		swaggerImport.doExecute(context);
	}
	
	@CitrusTest
	@Test @Parameters("context")
	public void publishToApprovalState(@Optional @CitrusResource TestContext context) throws IOException, AppException {
		swaggerImport = new ImportTestAction();
		
		description("A flag can be set to allow org-admins to publish up to the approval state.");
		
		variable("apiNumber", RandomNumberFunction.getRandomNumber(3, true));
		variable("apiPath", "/org-admin-published-approv-${apiNumber}");
		variable("apiName", "OrgAdmin-Published-Approv-${apiNumber}");
		variable("ignoreAdminAccount", "true"); // This tests simulate to use only an Org-Admin-Account

		echo("####### Calling the tool with a Non-Admin-User. #######");
		createVariable(ImportTestAction.API_DEFINITION,  "/com/axway/apim/test/files/basic/petstore.json");
		createVariable(ImportTestAction.API_CONFIG,  "/com/axway/apim/test/files/basic/2_initially_published.json");
		createVariable("expectedReturnCode", "0");
		createVariable("apiManagerUser", "${oadminUsername1}"); // This is an org-admin user
		createVariable("apiManagerPass", "${oadminPassword1}");
		swaggerImport.doExecute(context);
		
		http(builder -> builder.client("apiManager").send().get("/proxies").header("Content-Type", "application/json"));
		http(builder -> builder.client("apiManager").receive().response(HttpStatus.OK).messageType(MessageType.JSON)
			.validate("$.[?(@.path=='${apiPath}')].name", "${apiName}")
			.validate("$.[?(@.path=='${apiPath}')].state", "pending")
			.extractFromPayload("$.[?(@.path=='${apiPath}')].id", "apiId"));
		
		echo("####### Simulate an Administrator is now publishing this API #######");
		http(builder -> builder.client("apiManager").send().post("/proxies/${apiId}/publish").header("Content-Type", "application/x-www-form-urlencoded"));
		http(builder -> builder.client("apiManager").receive().response(HttpStatus.CREATED).messageType(MessageType.JSON)
				.validate("$.[?(@.id=='${apiId}')].name", "${apiName}")
				.validate("$.[?(@.id=='${apiId}')].state", "published"));
		
		echo("####### AT THIS POINT WE HAVE THE SAME API IN STATE PUBLISHED #######");
		
		echo("####### Trying to replicate the same API as unpublished (this should NOT conflict the with existing published API) #######");
		echo("####### Calling the tool with a Non-Admin-User. #######");
		createVariable(ImportTestAction.API_DEFINITION,  "/com/axway/apim/test/files/basic/petstore2.json");
		createVariable(ImportTestAction.API_CONFIG,  "/com/axway/apim/test/files/basic/1_no-change-config.json");
		createVariable("expectedReturnCode", "0");
		createVariable("apiManagerUser", "${oadminUsername1}"); // This is an org-admin user
		createVariable("apiManagerPass", "${oadminPassword1}");
		swaggerImport.doExecute(context);
		echo("####### Make sure, we have a second API with state unpublished #######");
		http(builder -> builder.client("apiManager").send().get("/proxies").header("Content-Type", "application/json"));
		http(builder -> builder.client("apiManager").receive().response(HttpStatus.OK).messageType(MessageType.JSON)
			.validate("$.[?(@.path=='${apiPath}' && @.id!='${apiId}')].name", "${apiName}")
			.validate("$.[?(@.path=='${apiPath}' && @.id!='${apiId}')].state", "unpublished")
			.extractFromPayload("$.[?(@.path=='${apiPath}' && @.id!='${apiId}')].id", "pendingApiId"));
		
		echo("####### Now updating the unpublished API to published which lead to a pending approval API #######");
		echo("####### Calling the tool with a Non-Admin-User. #######");
		createVariable(ImportTestAction.API_DEFINITION,  "/com/axway/apim/test/files/basic/petstore.json");
		createVariable(ImportTestAction.API_CONFIG,  "/com/axway/apim/test/files/basic/2_initially_published.json");
		createVariable("expectedReturnCode", "0");
		createVariable("apiManagerUser", "${oadminUsername1}"); // This is an org-admin user
		createVariable("apiManagerPass", "${oadminPassword1}");
		swaggerImport.doExecute(context);
		// Expection is to get a new API (ID) having a state pending!
		http(builder -> builder.client("apiManager").send().get("/proxies").header("Content-Type", "application/json"));
		http(builder -> builder.client("apiManager").receive().response(HttpStatus.OK).messageType(MessageType.JSON)
			.validate("$.[?(@.id=='${pendingApiId}' && @.id!='${apiId}')].name", "${apiName}")
			.validate("$.[?(@.id=='${pendingApiId}' && @.id!='${apiId}')].state", "pending")
			.extractFromPayload("$.[?(@.path=='${apiPath}' && @.id!='${apiId}')].id", "pendingApiId"));
		
		echo("####### As an OrgAdmin do some changes on the pending API (e.g. update the Swagger-File) #######");
		echo("####### This must lead to a new pending API #######");
		createVariable(ImportTestAction.API_DEFINITION,  "/com/axway/apim/test/files/basic/petstore2.json");
		createVariable(ImportTestAction.API_CONFIG,  "/com/axway/apim/test/files/basic/2_initially_published.json");
		createVariable("expectedReturnCode", "0");
		createVariable("apiManagerUser", "${oadminUsername1}"); // This is an org-admin user
		createVariable("apiManagerPass", "${oadminPassword1}");
		swaggerImport.doExecute(context);
		// Expection is to get a new API (ID) having a state pending!
		http(builder -> builder.client("apiManager").send().get("/proxies").header("Content-Type", "application/json"));
		http(builder -> builder.client("apiManager").receive().response(HttpStatus.OK).messageType(MessageType.JSON)
			.validate("$.[?(@.path=='${apiPath}' && @.id!='${apiId}' && @.id!='${pendingApiId}')].name", "${apiName}")
			.validate("$.[?(@.path=='${apiPath}' && @.id!='${apiId}' && @.id!='${pendingApiId}')].state", "pending")
			.validate("$.[?(@.path=='${apiPath}' && @.state=='pending')].id", "@assertThat(hasSize(1))@")); // Only one remaining pending API is expected
	}
}
