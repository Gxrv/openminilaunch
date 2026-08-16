#!/usr/bin/env ruby
# Generates deterministic A-Z contact fixtures for emulator testing.

require "fileutils"

output = File.expand_path("fixtures/minklauncher-test-contacts.vcf", __dir__)
FileUtils.mkdir_p(File.dirname(output))

names = [
  "Amara Bell", "Adrian Cole", "Avery Dunn",
  "Bianca Ellis", "Blake Foster", "Brielle Grant",
  "Callum Hayes", "Celeste Irwin", "Cyrus Jones",
  "Dahlia King", "Damian Lane", "Daphne Moore",
  "Elias North", "Elodie Owens", "Emerson Price",
  "Farah Quinn", "Felix Reed", "Freya Stone",
  "Gavin Tate", "Gemma Underwood", "Gideon Vale",
  "Hana Wells", "Harvey Young", "Hazel Zane",
  "Idris Abbott", "Imogen Birch", "Ivy Carter",
  "Jasper Dean", "Juno Evans", "Julian Frost",
  "Kaia Green", "Kieran Holt", "Kyla Innes",
  "Leona James", "Luca Kent", "Lyra Lowe",
  "Maya Marsh", "Micah Nolan", "Mira Oakley",
  "Nadia Pierce", "Nico Rowan", "Nola Sawyer",
  "Omar Turner", "Opal Vance", "Orion Webb",
  "Paloma Archer", "Parker Benson", "Priya Clarke",
  "Quentin Dorsey", "Quinn Everly", "Quiana Finch",
  "Rafael Grove", "Remy Hart", "Rhea Ives",
  "Soren Jade", "Selah Knox", "Simone Lake",
  "Talia Mercer", "Theo Nash", "Tessa Ormond",
  "Ulysses Park", "Uma Rivers", "Uri Sutton",
  "Vera Thorne", "Victor Ulman", "Viola West",
  "Willa Xavier", "Wesley York", "Wyatt Zephyr",
  "Xander Ames", "Xia Blair", "Ximena Cross",
  "Yara Drake", "Yusuf Emmett", "Yvette Flynn",
  "Zane Gray", "Zara Hughes", "Zuri Isaacs",
]

unless names.length == 78 && names.each_slice(3).with_index.all? { |group, index| group.all? { |name| name.start_with?((65 + index).chr) } }
  abort "Fixture names must contain exactly three names for every letter A-Z."
end

additional_numbers = {
  0 => ["work", 180], 7 => ["home", 181], 14 => ["work", 182],
  21 => ["home", 183], 28 => ["work", 184], 35 => ["home", 185],
  42 => ["work", 186], 49 => ["home", 187], 56 => ["work", 188],
  63 => ["home", 189], 70 => ["work", 190], 77 => ["home", 191],
}

cards = names.map.with_index do |name, index|
    first_name, last_name = name.split(" ", 2)
    number = 100 + index
    additional_phone = additional_numbers[index]
    additional_phone_line = if additional_phone
      type, suffix = additional_phone
      "TEL;TYPE=#{type}:+1-202-555-#{suffix.to_s.rjust(4, "0")}\n"
    else
      ""
    end
    <<~VCARD
      BEGIN:VCARD
      VERSION:4.0
      FN:#{name}
      N:#{last_name};#{first_name};;;
      TEL;TYPE=cell:+1-202-555-#{number.to_s.rjust(4, "0")}
      #{additional_phone_line}NOTE:MinkLauncher emulator fixture. Safe fictional 555 number.
      END:VCARD
    VCARD
end

File.write(output, cards.join)
puts "Wrote #{cards.length} contacts to #{output}"
