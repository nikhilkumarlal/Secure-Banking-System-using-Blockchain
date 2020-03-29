package io.sbs.service;

import static com.mongodb.client.model.Filters.eq;
import io.sbs.constant.UserType;
import io.sbs.dto.AppointmentDTO;
import io.sbs.dto.AuthenticationProfileDTO;
import io.sbs.dto.UserDTO;
import io.sbs.dto.WorkflowDTO;
import io.sbs.exception.BusinessException;
import io.sbs.exception.ValidationException;
import io.sbs.model.Account;
import io.sbs.model.User;
import io.sbs.model.Workflow;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;

import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.result.UpdateResult;

@Service
public class UserServiceImpl implements UserService {
	
	final MongoClient mongoClient = MongoClients.create("mongodb://admin:myadminpassword@18.222.64.16:27017");
	final MongoDatabase database = mongoClient.getDatabase("mydb");

	@Autowired
	private MongoTemplate mongoTemplate;
	
	@Autowired
	private Environment env;
	private static ApplicationContext applicationContext;
	
	@Override
	public List<Account> getUserAccountDetails(String username) {

	    MongoCollection<Document> collection = database.getCollection("user");
	    Document myDoc = collection.find(eq("username", username)).first();
	    
	    MongoCollection<Document> collection_acc = database.getCollection("account");
	    List<Document> cursor_accounts = collection_acc.find(eq("username", username)).into(new ArrayList<Document>());
	    
		List<Account> acc_list = new ArrayList<Account>();
	    for (Document account : cursor_accounts) {
	    	Account a = new Account();
	    	a.setAcc_holder_name(myDoc.get("name").toString());
	        a.setAccount_number(account.get("account_number").toString());
	        a.setAcc_type(account.get("acc_type").toString());
	        a.setAcc_balance(Double.parseDouble(account.get("acc_balance").toString()));
	        a.setUsername(username);
	        acc_list.add(a);
	    }

		return acc_list;		
	}

	@Override
	public User getUserInfo(String username) {
		MongoCollection<Document> collection = database.getCollection("user");
		Document myDoc = collection.find(eq("username", username)).first();
		User user = new User();
		if (myDoc.get("name") != null) user.setName(myDoc.get("name").toString());
		if (myDoc.get("email") != null) user.setEmailString(myDoc.get("email").toString());
		if (myDoc.get("address") != null) user.setAddress(myDoc.get("address").toString());
		return user;
	}
	
	// save workflow Object in the new user
	@Override
	public void register(UserDTO userDTO) {
		UserDTO dto = mongoTemplate.findOne(Query.query(Criteria.where("username").is(userDTO.getUsername())), UserDTO.class, "user");
		if (dto != null) {
			throw new ValidationException("the user already exists");
		}
		BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
		String hashedPassword = passwordEncoder.encode(userDTO.getPassword());
		userDTO.setPassword(hashedPassword);
		Date date= new Date();
		userDTO.setCreated_at(date);
		userDTO.setUpdated_at(date);
		Random rnd = new Random();
		double account_number = 10000000 + rnd.nextInt(90000000);
		userDTO.setAccount_number(account_number);
		WorkflowDTO workDTO=new WorkflowDTO();
		workDTO.setType(env.getProperty("type.register"));
		UserType usertype = null;
//		UserType usertype=userDTO.getRole();
//		if(usertype!=UserType.Tier1 || usertype!=UserType.Tier2 || usertype!=UserType.Customer)
//			throw new ValidationException("Invalid user role");
		List<UserDTO> details=new ArrayList<UserDTO>();
		details.add(userDTO);
		workDTO.setDetails(details);
		workDTO.setRole(usertype.Tier2); // hardCoded
		workDTO.setState("Pending");
		mongoTemplate.save(workDTO, "workflow");
	}

	
	@Override
	public UserDTO login(UserDTO userDTO) {
		BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
		UserDTO dto = mongoTemplate.findOne(Query.query(Criteria.where("username").is(userDTO.getUsername())), UserDTO.class, "user");
		if (dto == null) {
			throw new BusinessException("the account doesn't exist！");
		}

		if (!passwordEncoder.matches(userDTO.getPassword(), dto.getPassword())) {
			throw new BusinessException("password is wrong！");
		}
		EmailService es = new EmailService();
		String subject = "One Time Password (OTP) for Login";
		if(!es.send_email(dto.getUsername(), dto.getEmail(), subject)) {
			throw new BusinessException("Error in sending the email！");
		}
		dto.setPassword(null);
		return dto;
	}
	@Override
	public UserDTO updateUserInfo( UserDTO user) {

		WorkflowDTO workDTO=new WorkflowDTO();
		workDTO.setType(env.getProperty("type.updateUserInfo"));
//		UserType usertype=userDTO.getRole();
//		if(usertype!=UserType.Tier1 || usertype!=UserType.Tier2 || usertype!=UserType.Customer)
//			throw new ValidationException("Invalid user role");
		List<UserDTO> details=new ArrayList<UserDTO>();
		Date date = new Date();
		user.setUpdated_at(date);
		details.add(user);
		workDTO.setDetails(details);
		UserType usertype = null;
		workDTO.setRole(usertype.Tier2);
		workDTO.setState("Pending");
		mongoTemplate.save(workDTO, "workflow");
		return user;
	}

	@Override
	public WorkflowDTO updateDetails( WorkflowDTO workflowDTO) {
		LinkedHashMap map = (LinkedHashMap) workflowDTO.getDetails().get(0);
		Update update = new Update();
		if(map.get("address")!=null) {
			update.set("address", map.get("address").toString());
		}
		if(map.get("email")!=null) {
			update.set("email", map.get("email").toString());
		}
		
		UpdateResult userObj = mongoTemplate.updateFirst(Query.query(Criteria.where("username").is(map.get("username").toString())), update, User.class, "user");
		if (userObj == null) {
			throw new BusinessException("cannot be updated！");
		}
		return workflowDTO;
	}
	

	@Override
	public boolean checkAndMatchOTP(String username, String otp) {
		MongoCollection<Document> collection = database.getCollection("loginOTP");
		Document myDoc = collection.find(eq("username", username)).first();
		String otp_db = myDoc.get("otp").toString();
		if (otp_db.equals(otp)) {
			collection.updateOne(eq("username", username), new Document("$set", new Document("verified", true)));
			return true;
		}
		return false;
	}

	@Override
	public boolean forgotPasswordOTP(String username) {
		
		MongoCollection<Document> collection = database.getCollection("user");
		Document myDoc = collection.find(eq("username", username)).first();
		String email = myDoc.get("email").toString();
		if (email.isEmpty()) return false;
		EmailService es = new EmailService();
		String subject = "SBS Bank Password Reset OTP";
		es.send_email(username, email, subject);
		return true;
	}

	@Override

	public WorkflowDTO createUser(WorkflowDTO workflowDTO) {
//		UserDTO dto = mongoTemplate.findOne(Query.query(Criteria.where("username").is(userDTO.getUsername())), UserDTO.class, "user");
//		if (dto != null) {
//			throw new ValidationException("the user already exists");
//		}
		LinkedHashMap map = (LinkedHashMap) workflowDTO.getDetails().get(0);
		mongoTemplate.save(workflowDTO.getDetails().get(0), "user");
		AuthenticationProfileDTO authenticationProfileDTO = new AuthenticationProfileDTO();
		authenticationProfileDTO.setPassword(map.get("password").toString());
		authenticationProfileDTO.setUsername(map.get("username").toString());
		mongoTemplate.save(authenticationProfileDTO, "authenticationProfile");
		return workflowDTO;
	}
		

	public ResponseEntity<?> resetPass(String username, String currpassword, String newpassword) {
		System.out.println(username+currpassword);
		MongoCollection<Document> collection = database.getCollection("user");
		Document myDoc = collection.find(eq("username", username)).first();
		if (myDoc == null)
			return new ResponseEntity<>("The user does not exist. ", HttpStatus.BAD_REQUEST);
		BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
		String hashedPassword = passwordEncoder.encode(newpassword);
//		System.out.println(myDoc.get("password").toString());
//		System.out.println(passwordEncoder.encode("def"));
//		if (passwordEncoder.matches((CharSequence)currpassword, myDoc.get("password").toString()))
//			System.out.println("match");
//		else 
//			System.out.println("NOT match");
		collection.updateOne(eq("username", username), new Document("$set", new Document("password", newpassword)));
		MongoCollection<Document> c = database.getCollection("authenticationProfile");
		c.updateOne(eq("username", username), new Document("$set", new Document("password", newpassword)));
		return new ResponseEntity<>("Password successfully updated.", HttpStatus.OK);

	}

	@Override
	public ResponseEntity<?> addAcc(String username, Account acc) {
		EmailService es = new EmailService();
		String acc_num = new String(es.generate_random(9));
		MongoCollection<Document> collection = database.getCollection("user");
		Document myDoc = collection.find(eq("username", username)).first();
		if (myDoc == null)
			return new ResponseEntity<>("No user found. ", HttpStatus.OK);
		collection = database.getCollection("account");
		myDoc = collection.find(eq("account_number", acc_num)).first();
		if (myDoc != null)
			acc_num = new String(es.generate_random(9));

		Document doc = new Document("username", username)
                .append("acc_type", acc.getAcc_type())
                .append("acc_holder_name", acc.getAcc_holder_name())
                .append("acc_balance", acc.getAcc_balance())
                .append("account_number", "9"+ acc_num);
		collection.insertOne(doc);
		return new ResponseEntity<>("Successfully added new account ", HttpStatus.OK);
	}

	@Override
	public ResponseEntity<?> generateChequeService(String username, Account acc) {
		MongoCollection<Document> collection = database.getCollection("user");
		Document myDoc = collection.find(eq("username", username)).first();
		String email = myDoc.getString("email");
		EmailService es = new EmailService();
		collection = database.getCollection("account");
		myDoc = collection.find(eq("username", username)).first();
		if (myDoc == null)
			return new ResponseEntity<>("No username found. ", HttpStatus.OK);

		myDoc = collection.find(eq("account_number", acc.getAccount_number())).first();
		if (myDoc == null)
			return new ResponseEntity<>("No account found. ", HttpStatus.OK);

		double balance = myDoc.getDouble("acc_balance");
		balance = balance - acc.getAmount_to_debit();
		collection.updateOne(eq("account_number", acc.getAccount_number()), new Document("$set", new Document("acc_balance", balance)));
		es.send_email_cheque_success(email, "Cashier Cheque Issued", acc.getAmount_to_debit());
		return new ResponseEntity<>("Successfully issued new cheque ", HttpStatus.OK);
	}

	@Override
	public ResponseEntity<?> debitAmountService(String username, Account acc) {
		MongoCollection<Document> collection = database.getCollection("account");
		Document myDoc = collection.find(eq("username", username)).first();
		if (myDoc == null)
			return new ResponseEntity<>("No username found. ", HttpStatus.OK);

		myDoc = collection.find(eq("account_number", acc.getAccount_number())).first();
		if (myDoc == null)
			return new ResponseEntity<>("No account found. ", HttpStatus.OK);
		
		double balance = myDoc.getDouble("acc_balance");
		balance += acc.getAmount_to_credit();
		collection.updateOne(eq("account_number", acc.getAccount_number()), new Document("$set", new Document("acc_balance", balance)));
		return new ResponseEntity<>("Amount successfully debited from account. ", HttpStatus.OK);

	}

	@Override
	public AppointmentDTO createAppointment(AppointmentDTO appointmentDTO) {
		WorkflowDTO workDTO=new WorkflowDTO();
		workDTO.setType(env.getProperty("type.createAppointment"));
//		UserType usertype=userDTO.getRole();
//		if(usertype!=UserType.Tier1 || usertype!=UserType.Tier2 || usertype!=UserType.Customer)
//			throw new ValidationException("Invalid user role");
		List<AppointmentDTO> details=new ArrayList<AppointmentDTO>();
		Date date = new Date();  
		appointmentDTO.setCreated_at(date);
		details.add(appointmentDTO);
		workDTO.setDetails(details);
		UserType usertype = null;
		workDTO.setRole(usertype.Tier1);
		workDTO.setState("Pending");
		mongoTemplate.save(workDTO, "workflow");
		return appointmentDTO;
	}
	
	@Override
	public WorkflowDTO createAppointments(WorkflowDTO workflowDTO) {
//		// TODO Auto-generated method stub
		
		LinkedHashMap map = (LinkedHashMap) workflowDTO.getDetails().get(0);
		System.out.println(mongoTemplate);
		UserDTO dto = mongoTemplate.findOne(Query.query(Criteria.where("username").is(map.get("username").toString())), UserDTO.class, "user");
		
		if (dto == null) {
			throw new BusinessException("User not found!");
		}
		
		EmailService es = new EmailService();
		String subject = "Appointment created";
		if(!es.send_email(dto.getUsername(), dto.getEmail(), subject)) {
			throw new BusinessException("Error in sending the email！");
		}
		return workflowDTO;
	}

	@Override
	public WorkflowDTO updateStateOfWorkflow(WorkflowDTO workflowDTO) {
		Update update = new Update();
		update.set("state", workflowDTO.getState());
		UpdateResult userObj = mongoTemplate.updateFirst(Query.query(Criteria.where("workflow_id").is(workflowDTO.getWorkflow_id())), update, WorkflowDTO.class, "workflow");
		return workflowDTO;
	}

}
