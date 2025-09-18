import commons.serviceportal.data.NationDataHelper
import de.seitenbau.serviceportal.scripting.api.v1.form.PossibleValueListV1
import spock.lang.Specification

class NationDataHelperSpecification extends Specification {
  def "Query nation database for Germany"() {
    given:
    NationDataHelper.Nation germany = NationDataHelper.nations.find { it.searchTerm == "Deutschland" }

    expect:
    NationDataHelper.nations.size() > 1

    germany.officialTermShort == "Deutschland"
    germany.officialTermLong == "die Bundesrepublik Deutschland"
    germany.continent == NationDataHelper.Continent.EUR
    germany.citizenship == "deutsch"
    germany.iso3166_2 == "DE"
    germany.iso3166_3 == "DEU"
    germany.isEu
  }

  def "Adding new nations to a supposedly immutable Set"() {
    when:
    NationDataHelper.nations.add(new NationDataHelper.Nation())

    then:
    thrown(UnsupportedOperationException)
  }

  def "Creating a PossibleValueList"() {
    when:
    PossibleValueListV1 pvList = NationDataHelper.toPossibleValueList("iso3166_2", "officialTermShort")

    then:
    pvList.find { it.value == "DE" }.label == "Deutschland"
  }

  def "Creating a PossibleValueList with invalid configuration"() {
    when:
    PossibleValueListV1 pvList = NationDataHelper.toPossibleValueList("iso3166_2", "does_not_exist")

    then:
    thrown(IllegalArgumentException)
  }

}
