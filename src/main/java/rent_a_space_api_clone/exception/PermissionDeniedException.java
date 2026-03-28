package rent_a_space_api_clone.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.FORBIDDEN, reason = "Not allowed to perform the operation")
public class PermissionDeniedException extends RuntimeException {
    public PermissionDeniedException(String s) {
        super(s);
    }
}
