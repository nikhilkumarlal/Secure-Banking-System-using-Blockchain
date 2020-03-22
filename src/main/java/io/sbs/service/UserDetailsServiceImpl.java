package io.sbs.service;

import io.sbs.dto.UserDTO;
//import io.sbs.model.User;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;

import static java.util.Collections.emptyList;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {
	// private ApplicationUserRepository applicationUserRepository;

	// public UserDetailsServiceImpl(ApplicationUserRepository
	// applicationUserRepository) {
	// this.applicationUserRepository = applicationUserRepository;
	// }

	final MongoClient mongoClient = MongoClients
			.create("mongodb://admin:myadminpassword@18.222.64.16:27017");
	final MongoDatabase database = mongoClient.getDatabase("mydb");

	@Autowired
	private MongoTemplate mongoTemplate;

	@Override
	public UserDetails loadUserByUsername(String username)
			throws UsernameNotFoundException {
		// BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
		Query query = new Query(Criteria.where("username"));
		UserDTO dto = mongoTemplate.findOne(
				Query.query(Criteria.where("username").is(username)),
				UserDTO.class, "user");
		if (dto == null) {
			throw new UsernameNotFoundException(username);
			// throw new BusinessException("the account doesn't register！");
		}
		// if (!passwordEncoder.matches(userDTO.getPassword(),
		// dto.getPassword())) {
		// throw new BusinessException("password is wrong！");
		// }

		// ApplicationUser applicationUser = applicationUserRepository
		// .findByUsername(username);
		// if (applicationUser == null) {
		// throw new UsernameNotFoundException(username);
		// }
		return new User(dto.getUsername(), dto.getPassword(), emptyList());
	}
}
