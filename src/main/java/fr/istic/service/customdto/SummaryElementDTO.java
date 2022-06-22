package fr.istic.service.customdto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@RegisterForReflection
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SummaryElementDTO {
	Long id;

	int firstUnratedStd;

	int nbRated;
}
