package io.sbs.repository;
import io.sbs.model.User;

import org.springframework.data.mongodb.repository.MongoRepository;



public interface UserRepository extends MongoRepository<User,String> {

//	public String getAll() {
//		return "from DAO Users layer, Hello World";
//	}

}