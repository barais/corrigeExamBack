package fr.istic.service.customdto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@RegisterForReflection
@Getter
@Setter
@AllArgsConstructor
public class SummaryExamDTO {
	public final List<SummaryElementDTO> questions;

	public final List<SummaryElementDTO> sheets;

	public SummaryExamDTO() {
		super();
		questions = new ArrayList<>();
		sheets = new ArrayList<>();
	}
}
